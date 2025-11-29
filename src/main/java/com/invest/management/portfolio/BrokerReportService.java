package com.invest.management.portfolio;

import com.invest.management.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Service
public class BrokerReportService {

    private static final Logger log = LoggerFactory.getLogger(BrokerReportService.class);

    private final BrokerReportParser parser;
    private final BrokerReportRepository reportRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioCashMovementRepository cashMovementRepository;
    private final PortfolioPositionService positionService;
    private final com.invest.management.moex.MoexStockRepository stockRepository;
    private final com.invest.management.moex.bond.BondRepository bondRepository;

    public BrokerReportService(BrokerReportParser parser,
                               BrokerReportRepository reportRepository,
                               PortfolioTransactionRepository transactionRepository,
                               PortfolioCashMovementRepository cashMovementRepository,
                               PortfolioPositionService positionService,
                               com.invest.management.moex.MoexStockRepository stockRepository,
                               com.invest.management.moex.bond.BondRepository bondRepository) {
        this.parser = parser;
        this.reportRepository = reportRepository;
        this.transactionRepository = transactionRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.positionService = positionService;
        this.stockRepository = stockRepository;
        this.bondRepository = bondRepository;
    }

    @Transactional
    public void processReport(Portfolio portfolio, MultipartFile file, AppUser uploadedBy) {
        String fileName = file.getOriginalFilename();
        
        try (InputStream inputStream = file.getInputStream()) {
            BrokerReportParser.ParsedReport parsed = parser.parse(inputStream, fileName);

            // Проверка на дубликаты (точное совпадение периода)
            Optional<BrokerReport> existingReport = reportRepository.findByPortfolioAndReportPeriodStartAndReportPeriodEnd(
                    portfolio,
                    parsed.getReportPeriodStart(),
                    parsed.getReportPeriodEnd());

            if (existingReport.isPresent()) {
                throw new IllegalArgumentException(
                    String.format("Отчет за период %s - %s уже загружен",
                        parsed.getReportPeriodStart(), parsed.getReportPeriodEnd()));
            }

            // Проверка на пересечение периодов
            // Разрешаем загрузку более широкого периода, если более узкий уже загружен
            // Блокируем загрузку более узкого периода, если более широкий уже загружен
            List<BrokerReport> overlappingReports = reportRepository.findOverlappingReports(
                    portfolio,
                    parsed.getReportPeriodStart(),
                    parsed.getReportPeriodEnd());

            if (!overlappingReports.isEmpty()) {
                for (BrokerReport overlapping : overlappingReports) {
                    // Проверяем, является ли новый период более узким, чем существующий
                    boolean newPeriodIsNarrower = 
                        (parsed.getReportPeriodStart().isAfter(overlapping.getReportPeriodStart()) ||
                         parsed.getReportPeriodStart().equals(overlapping.getReportPeriodStart())) &&
                        (parsed.getReportPeriodEnd().isBefore(overlapping.getReportPeriodEnd()) ||
                         parsed.getReportPeriodEnd().equals(overlapping.getReportPeriodEnd())) &&
                        !(parsed.getReportPeriodStart().equals(overlapping.getReportPeriodStart()) &&
                          parsed.getReportPeriodEnd().equals(overlapping.getReportPeriodEnd()));

                    if (newPeriodIsNarrower) {
                        throw new IllegalArgumentException(
                            String.format("Отчет за период %s - %s пересекается с уже загруженным отчетом за период %s - %s",
                                parsed.getReportPeriodStart(), parsed.getReportPeriodEnd(),
                                overlapping.getReportPeriodStart(), overlapping.getReportPeriodEnd()));
                    }
                    // Если новый период шире или равен существующему, разрешаем загрузку
                }
            }

            // Сохраняем метаданные отчета
            BrokerReport report = new BrokerReport();
            report.setPortfolio(portfolio);
            report.setFileName(fileName);
            report.setReportPeriodStart(parsed.getReportPeriodStart());
            report.setReportPeriodEnd(parsed.getReportPeriodEnd());
            report.setReportCreatedDate(parsed.getReportCreatedDate());
            report.setInvestorName(parsed.getInvestorName());
            report.setContractNumber(parsed.getContractNumber());
            report.setUploadedBy(uploadedBy);
            reportRepository.save(report);

            // Сохраняем транзакции с проверкой на дубликаты по номеру сделки
            for (PortfolioTransaction transaction : parsed.getTransactions()) {
                transaction.setPortfolio(portfolio);
                
                // Проверка на дубликат по номеру сделки
                if (transaction.getTradeNumber() != null && !transaction.getTradeNumber().isBlank()) {
                    Optional<PortfolioTransaction> existingTransaction = 
                        transactionRepository.findByPortfolioAndTradeNumber(
                            portfolio, transaction.getTradeNumber());
                    
                    if (existingTransaction.isPresent()) {
                        log.warn("Транзакция с номером сделки {} уже существует, пропускаем", 
                            transaction.getTradeNumber());
                        continue; // Пропускаем дубликат
                    }
                }
                
                transactionRepository.save(transaction);
            }

            // Удаляем движения из перекрывающихся отчетов, которые полностью попадают в период нового отчета
            // Это предотвращает дубликаты при загрузке месячного отчета после дневных
            for (BrokerReport overlapping : overlappingReports) {
                // Проверяем, полностью ли перекрывающийся отчет попадает в период нового отчета
                boolean isFullyContained = 
                    (overlapping.getReportPeriodStart().isAfter(parsed.getReportPeriodStart()) ||
                     overlapping.getReportPeriodStart().equals(parsed.getReportPeriodStart())) &&
                    (overlapping.getReportPeriodEnd().isBefore(parsed.getReportPeriodEnd()) ||
                     overlapping.getReportPeriodEnd().equals(parsed.getReportPeriodEnd()));
                
                if (isFullyContained) {
                    log.info("Удаление движений денежных средств из отчета за период {} - {} (полностью попадает в период нового отчета)",
                        overlapping.getReportPeriodStart(), overlapping.getReportPeriodEnd());
                    cashMovementRepository.deleteByReport(overlapping);
                }
            }

            // Сохраняем движение денежных средств с привязкой к отчету
            for (PortfolioCashMovement movement : parsed.getCashMovements()) {
                movement.setPortfolio(portfolio);
                movement.setReport(report);
                cashMovementRepository.save(movement);
            }

            // Обновляем конечные позиции только если новый отчет новее или равен максимальному периоду
            Optional<java.time.LocalDate> maxPeriodEnd = reportRepository.findMaxReportPeriodEnd(portfolio);
            boolean shouldUpdatePositions = maxPeriodEnd.isEmpty() || 
                !parsed.getReportPeriodEnd().isBefore(maxPeriodEnd.get());
            
            if (shouldUpdatePositions) {
                log.info("Обновление конечных позиций для портфеля {} (отчет за период {} - {} новее или равен максимальному периоду {})",
                    portfolio.getId(), parsed.getReportPeriodStart(), parsed.getReportPeriodEnd(), 
                    maxPeriodEnd.orElse(null));
                positionService.setEndPeriodPositions(portfolio, parsed.getEndPeriodPositions());
                
                // Пересчитываем среднюю цену приобретения на основе всех транзакций покупки
                positionService.recalculateAveragePrices(portfolio);
            } else {
                log.info("Пропуск обновления конечных позиций для портфеля {} (отчет за период {} - {} старше максимального периода {})",
                    portfolio.getId(), parsed.getReportPeriodStart(), parsed.getReportPeriodEnd(), maxPeriodEnd.get());
            }

            log.info("Обработан отчет {} для портфеля {}: {} транзакций, {} движений денежных средств, {} конечных позиций",
                    fileName, portfolio.getId(), parsed.getTransactions().size(), parsed.getCashMovements().size(), 
                    parsed.getEndPeriodPositions().size());

        } catch (IllegalArgumentException e) {
            // IllegalArgumentException - это валидное бизнес-исключение, пробрасываем как есть
            log.error("Ошибка при обработке отчета {}: {}", fileName, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при обработке отчета {}: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Не удалось обработать отчет: " + e.getMessage(), e);
        }
    }

    public List<BrokerReport> getPortfolioReports(Portfolio portfolio) {
        return reportRepository.findByPortfolioOrderByReportPeriodStartDesc(portfolio);
    }

    /**
     * Проверяет, является ли это первым отчетом для портфеля
     * @param portfolio портфель
     * @return true, если это первый отчет
     */
    public boolean isFirstReport(Portfolio portfolio) {
        return reportRepository.findByPortfolioOrderByReportPeriodStartDesc(portfolio).isEmpty();
    }

    /**
     * Получает позиции, которые требуют ввода цены приобретения
     * (позиции с quantity > 0 и averagePurchasePrice == null)
     * @param portfolio портфель
     * @return список позиций, требующих ввода цены
     */
    public List<PortfolioPosition> getPositionsRequiringPriceInput(Portfolio portfolio) {
        List<PortfolioPosition> positions = positionService.getCurrentPositions(portfolio);
        return positions.stream()
            .filter(p -> p.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0)
            .filter(p -> p.getAveragePurchasePrice() == null)
            .toList();
    }

    /**
     * Получает позиции с названиями инструментов для отображения в модальном окне
     * @param portfolio портфель
     * @return список позиций с названиями
     */
    public List<PositionWithName> getPositionsWithNamesRequiringPriceInput(Portfolio portfolio) {
        List<PortfolioPosition> positions = getPositionsRequiringPriceInput(portfolio);
        return positions.stream()
            .map(p -> {
                String name = getSecurityName(p.getIsin(), p.getSecurityType());
                return new PositionWithName(p, name);
            })
            .toList();
    }

    private String getSecurityName(String isin, String securityType) {
        if ("STOCK".equals(securityType)) {
            return stockRepository.findByIsin(isin)
                .map(stock -> stock.getShortname() != null ? stock.getShortname() : isin)
                .orElse(isin);
        } else if ("BOND".equals(securityType)) {
            return bondRepository.findByIsin(isin)
                .map(bond -> bond.getShortname() != null ? bond.getShortname() : isin)
                .orElse(isin);
        }
        return isin;
    }

    public static class PositionWithName {
        private final PortfolioPosition position;
        private final String name;

        public PositionWithName(PortfolioPosition position, String name) {
            this.position = position;
            this.name = name;
        }

        public PortfolioPosition getPosition() {
            return position;
        }

        public String getName() {
            return name;
        }
    }
}

