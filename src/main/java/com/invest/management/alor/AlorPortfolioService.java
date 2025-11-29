package com.invest.management.alor;

import com.invest.management.alor.common.AlorApiClient;
import com.invest.management.alor.dto.AlorPosition;
import com.invest.management.moex.MoexStockRepository;
import com.invest.management.moex.bond.BondRepository;
import com.invest.management.portfolio.Portfolio;
import com.invest.management.portfolio.PortfolioPosition;
import com.invest.management.portfolio.PortfolioPositionRepository;
import com.invest.management.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис для синхронизации портфеля из ALOR API
 */
@Service
public class AlorPortfolioService {

    private static final Logger log = LoggerFactory.getLogger(AlorPortfolioService.class);

    private final AlorApiClient alorApiClient;
    private final AlorIsinResolver isinResolver;
    private final PortfolioPositionRepository positionRepository;
    private final MoexStockRepository stockRepository;
    private final BondRepository bondRepository;

    public AlorPortfolioService(AlorApiClient alorApiClient,
                                AlorIsinResolver isinResolver,
                                PortfolioPositionRepository positionRepository,
                                MoexStockRepository stockRepository,
                                BondRepository bondRepository) {
        this.alorApiClient = alorApiClient;
        this.isinResolver = isinResolver;
        this.positionRepository = positionRepository;
        this.stockRepository = stockRepository;
        this.bondRepository = bondRepository;
    }

    /**
     * Результат синхронизации позиций
     */
    public static class SyncResult {
        private int totalPositions;
        private int successfulPositions;
        private int skippedPositions;
        private List<String> problematicPositions = new ArrayList<>();
        private BigDecimal freeCashRUB = BigDecimal.ZERO;

        public int getTotalPositions() {
            return totalPositions;
        }

        public void setTotalPositions(int totalPositions) {
            this.totalPositions = totalPositions;
        }

        public int getSuccessfulPositions() {
            return successfulPositions;
        }

        public void setSuccessfulPositions(int successfulPositions) {
            this.successfulPositions = successfulPositions;
        }

        public int getSkippedPositions() {
            return skippedPositions;
        }

        public void setSkippedPositions(int skippedPositions) {
            this.skippedPositions = skippedPositions;
        }

        public List<String> getProblematicPositions() {
            return problematicPositions;
        }

        public void setProblematicPositions(List<String> problematicPositions) {
            this.problematicPositions = problematicPositions;
        }

        public BigDecimal getFreeCashRUB() {
            return freeCashRUB;
        }

        public void setFreeCashRUB(BigDecimal freeCashRUB) {
            this.freeCashRUB = freeCashRUB;
        }
    }

    /**
     * Синхронизирует позиции портфеля из ALOR API
     * 
     * @param portfolio портфель
     * @param user пользователь
     * @param environment окружение (test/production)
     * @param alorPortfolioId ID портфеля в ALOR
     * @param exchange биржа (по умолчанию MOEX)
     * @return результат синхронизации
     */
    @Transactional
    public SyncResult syncPositionsFromAlor(Portfolio portfolio, 
                                           AppUser user, 
                                           String environment, 
                                           String alorPortfolioId, 
                                           String exchange) {
        log.info("Начало синхронизации позиций из ALOR для портфеля {} (ALOR portfolio: {})", 
                portfolio.getId(), alorPortfolioId);

        SyncResult result = new SyncResult();

        // Получаем позиции из ALOR
        List<AlorPosition> alorPositions = alorApiClient.getPositions(user, environment, alorPortfolioId, exchange);
        result.setTotalPositions(alorPositions.size());

        if (alorPositions.isEmpty()) {
            log.warn("Не получено позиций из ALOR для портфеля {}", portfolio.getId());
            return result;
        }

        // Получаем существующие позиции портфеля
        List<PortfolioPosition> existingPositions = positionRepository.findByPortfolio(portfolio);
        Set<String> existingIsins = new HashSet<>();
        for (PortfolioPosition existing : existingPositions) {
            existingIsins.add(existing.getIsin());
        }

        // Множество ISIN, которые есть в ALOR (для удаления позиций, которых нет в ALOR)
        Set<String> alorIsins = new HashSet<>();

        // Обрабатываем каждую позицию из ALOR
        for (AlorPosition alorPosition : alorPositions) {
            // Пропускаем валютные позиции (обрабатываем отдельно для свободных средств)
            if (Boolean.TRUE.equals(alorPosition.getIsCurrency())) {
                if ("RUB".equals(alorPosition.getSymbol()) && alorPosition.getQtyUnits() != null) {
                    result.setFreeCashRUB(result.getFreeCashRUB().add(alorPosition.getQtyUnits()));
                }
                result.setSkippedPositions(result.getSkippedPositions() + 1);
                continue;
            }

            // Определяем ISIN
            Optional<String> isinOpt = isinResolver.resolveIsin(alorPosition);
            if (isinOpt.isEmpty()) {
                String positionInfo = String.format("%s (%s)", 
                        alorPosition.getShortName() != null ? alorPosition.getShortName() : alorPosition.getSymbol(),
                        alorPosition.getSymbol());
                result.getProblematicPositions().add(positionInfo);
                result.setSkippedPositions(result.getSkippedPositions() + 1);
                log.warn("Пропущена позиция из-за отсутствия ISIN: {}", positionInfo);
                continue;
            }

            String isin = isinOpt.get();
            alorIsins.add(isin);

            // Определяем тип инструмента
            String securityType = isinResolver.determineSecurityType(alorPosition);
            if (securityType == null) {
                securityType = "STOCK"; // По умолчанию, если не удалось определить
            }

            // Находим или создаем позицию
            Optional<PortfolioPosition> existingPositionOpt = positionRepository.findByPortfolioAndIsin(portfolio, isin);
            PortfolioPosition position;
            if (existingPositionOpt.isPresent()) {
                position = existingPositionOpt.get();
                log.debug("Обновление существующей позиции: ISIN={}, quantity={} -> {}", 
                        isin, position.getQuantity(), alorPosition.getQtyUnits());
            } else {
                position = new PortfolioPosition();
                position.setPortfolio(portfolio);
                position.setIsin(isin);
                log.debug("Создание новой позиции: ISIN={}, quantity={}", isin, alorPosition.getQtyUnits());
            }

            // Обновляем данные позиции
            if (alorPosition.getQtyUnits() != null) {
                position.setQuantity(alorPosition.getQtyUnits());
            } else if (alorPosition.getQty() != null && alorPosition.getLotSize() != null && alorPosition.getLotSize() > 0) {
                // Если qtyUnits нет, вычисляем из qty * lotSize
                position.setQuantity(alorPosition.getQty().multiply(BigDecimal.valueOf(alorPosition.getLotSize())));
            } else {
                log.warn("Не удалось определить quantity для позиции ISIN={}", isin);
                result.getProblematicPositions().add(String.format("%s (ISIN: %s) - не удалось определить количество", 
                        alorPosition.getShortName() != null ? alorPosition.getShortName() : alorPosition.getSymbol(), isin));
                result.setSkippedPositions(result.getSkippedPositions() + 1);
                continue;
            }

            // Обновляем среднюю цену покупки
            if (alorPosition.getAvgPrice() != null) {
                position.setAveragePurchasePrice(alorPosition.getAvgPrice());
            }

            // Вычисляем текущую цену из currentVolume / qtyUnits
            if (alorPosition.getCurrentVolume() != null && alorPosition.getQtyUnits() != null 
                    && alorPosition.getQtyUnits().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentPrice = alorPosition.getCurrentVolume()
                        .divide(alorPosition.getQtyUnits(), 6, RoundingMode.HALF_UP);
                position.setLastKnownPrice(currentPrice);
            }

            // Обновляем тип инструмента
            position.setSecurityType(securityType);
            
            // Устанавливаем связи с MOEX справочниками
            setMoexRelations(position, isin, securityType);

            // Сохраняем позицию
            positionRepository.save(position);
            result.setSuccessfulPositions(result.getSuccessfulPositions() + 1);
        }

        // Удаляем позиции, которых нет в ALOR
        Set<String> isinsToDelete = new HashSet<>(existingIsins);
        isinsToDelete.removeAll(alorIsins);
        for (String isinToDelete : isinsToDelete) {
            Optional<PortfolioPosition> positionToDelete = positionRepository.findByPortfolioAndIsin(portfolio, isinToDelete);
            if (positionToDelete.isPresent()) {
                positionRepository.delete(positionToDelete.get());
                log.debug("Удалена позиция, которой нет в ALOR: ISIN={}", isinToDelete);
            }
        }

        log.info("Синхронизация завершена для портфеля {}: успешно={}, пропущено={}, проблемных={}, удалено={}, свободные средства RUB={}", 
                portfolio.getId(), result.getSuccessfulPositions(), result.getSkippedPositions(), 
                result.getProblematicPositions().size(), isinsToDelete.size(), result.getFreeCashRUB());

        return result;
    }

    /**
     * Устанавливает связи с MOEX справочниками на основе ISIN и типа инструмента
     */
    private void setMoexRelations(PortfolioPosition position, String isin, String securityType) {
        if ("STOCK".equals(securityType)) {
            Optional<com.invest.management.moex.MoexStock> stockOpt = stockRepository.findByIsin(isin);
            if (stockOpt.isPresent()) {
                position.setMoexStock(stockOpt.get());
            }
        } else if ("BOND".equals(securityType)) {
            Optional<com.invest.management.moex.bond.Bond> bondOpt = bondRepository.findByIsin(isin);
            if (bondOpt.isPresent()) {
                position.setMoexBond(bondOpt.get());
            }
        }
    }
}

