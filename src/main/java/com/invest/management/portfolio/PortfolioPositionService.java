package com.invest.management.portfolio;

import com.invest.management.moex.MoexStockRepository;
import com.invest.management.moex.bond.BondRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PortfolioPositionService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPositionService.class);

    private final PortfolioPositionRepository positionRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final MoexStockRepository stockRepository;
    private final BondRepository bondRepository;

    public PortfolioPositionService(PortfolioPositionRepository positionRepository,
                                    PortfolioTransactionRepository transactionRepository,
                                    MoexStockRepository stockRepository,
                                    BondRepository bondRepository) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.stockRepository = stockRepository;
        this.bondRepository = bondRepository;
    }

    @Transactional
    public void recalculatePositions(Portfolio portfolio) {
        // Удаляем все текущие позиции
        positionRepository.deleteAllByPortfolio(portfolio);

        // Получаем все транзакции портфеля, отсортированные по дате
        List<PortfolioTransaction> transactions = transactionRepository.findByPortfolioOrderByTradeDateAsc(portfolio);

        // Вычисляем позиции из транзакций
        Map<String, PositionAccumulator> positions = new HashMap<>();

        for (PortfolioTransaction transaction : transactions) {
            String isin = transaction.getIsin();
            PositionAccumulator acc = positions.computeIfAbsent(isin, k -> {
                PositionAccumulator newAcc = new PositionAccumulator();
                newAcc.isin = isin;
                newAcc.securityType = transaction.getSecurityType();
                return newAcc;
            });

            if ("Покупка".equals(transaction.getOperationType())) {
                acc.quantity = acc.quantity.add(transaction.getQuantity());
            } else if ("Продажа".equals(transaction.getOperationType())) {
                acc.quantity = acc.quantity.subtract(transaction.getQuantity());
            }
        }

        // Сохраняем позиции (только те, где quantity > 0)
        for (PositionAccumulator acc : positions.values()) {
            if (acc.quantity.compareTo(BigDecimal.ZERO) > 0) {
                PortfolioPosition position = new PortfolioPosition();
                position.setPortfolio(portfolio);
                position.setIsin(acc.isin);
                position.setSecurityType(acc.securityType);
                position.setQuantity(acc.quantity);
                setMoexRelations(position, acc.isin, acc.securityType);
                positionRepository.save(position);
            }
        }

        log.info("Пересчитано позиций для портфеля {}: {}", portfolio.getId(), positions.size());
    }

    /**
     * Устанавливает позиции портфеля на основе конечных позиций из отчета
     * Сохраняет существующую среднюю цену приобретения, если она есть
     * @param portfolio портфель
     * @param endPositions конечные позиции из отчета
     */
    @Transactional
    public void setEndPeriodPositions(Portfolio portfolio, List<BrokerReportParser.EndPeriodPosition> endPositions) {
        // Получаем существующие позиции для сохранения средней цены
        List<PortfolioPosition> existingPositions = positionRepository.findByPortfolio(portfolio);
        Map<String, BigDecimal> existingAveragePrices = new HashMap<>();
        for (PortfolioPosition existing : existingPositions) {
            if (existing.getAveragePurchasePrice() != null) {
                existingAveragePrices.put(existing.getIsin(), existing.getAveragePurchasePrice());
            }
        }

        // Удаляем все текущие позиции
        positionRepository.deleteAllByPortfolio(portfolio);

        // Сохраняем позиции из отчета
        for (BrokerReportParser.EndPeriodPosition endPos : endPositions) {
            PortfolioPosition position = new PortfolioPosition();
            position.setPortfolio(portfolio);
            position.setIsin(endPos.getIsin());
            position.setSecurityType(endPos.getSecurityType());
            position.setQuantity(endPos.getQuantity());
            
            // Устанавливаем связи с MOEX справочниками
            setMoexRelations(position, endPos.getIsin(), endPos.getSecurityType());
            
            // Сохраняем название из отчета (fallback для погашенных облигаций)
            if (endPos.getSecurityName() != null && !endPos.getSecurityName().isEmpty()) {
                position.setSecurityName(endPos.getSecurityName());
            }
            
            // Сохраняем цену из отчета
            if (endPos.getLastKnownPrice() != null) {
                position.setLastKnownPrice(endPos.getLastKnownPrice());
            }
            
            // Сохраняем существующую среднюю цену, если она есть
            if (existingAveragePrices.containsKey(endPos.getIsin())) {
                position.setAveragePurchasePrice(existingAveragePrices.get(endPos.getIsin()));
            }
            positionRepository.save(position);
        }

        log.info("Установлено позиций для портфеля {} из отчета: {}", portfolio.getId(), endPositions.size());
    }

    /**
     * Обновляет среднюю цену приобретения при транзакции
     * @param portfolio портфель
     * @param transaction транзакция
     */
    @Transactional
    public void updateAveragePriceOnTransaction(Portfolio portfolio, PortfolioTransaction transaction) {
        if (!"Покупка".equals(transaction.getOperationType())) {
            // При продаже средняя цена не меняется
            return;
        }

        Optional<PortfolioPosition> positionOpt = positionRepository.findByPortfolioAndIsin(
            portfolio, transaction.getIsin());

        if (positionOpt.isEmpty()) {
            log.warn("Позиция для ISIN {} не найдена при обновлении средней цены", transaction.getIsin());
            return;
        }

        PortfolioPosition position = positionOpt.get();
        BigDecimal currentQuantity = position.getQuantity();
        BigDecimal currentAvgPrice = position.getAveragePurchasePrice();
        BigDecimal transactionQuantity = transaction.getQuantity();
        BigDecimal transactionPrice = transaction.getPrice();

        BigDecimal newAveragePrice;
        if (currentAvgPrice == null || currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
            // Если средней цены нет или количество было 0, используем цену транзакции
            newAveragePrice = transactionPrice;
        } else {
            // Пересчитываем средневзвешенную цену
            // new_avg = (old_avg * old_qty + price * qty) / (old_qty + qty)
            BigDecimal totalCost = currentAvgPrice.multiply(currentQuantity)
                .add(transactionPrice.multiply(transactionQuantity));
            BigDecimal totalQuantity = currentQuantity.add(transactionQuantity);
            newAveragePrice = totalCost.divide(totalQuantity, 6, RoundingMode.HALF_UP);
        }

        position.setAveragePurchasePrice(newAveragePrice);
        positionRepository.save(position);
        log.debug("Обновлена средняя цена для ISIN {}: {} -> {}", 
            transaction.getIsin(), currentAvgPrice, newAveragePrice);
    }

    public List<PortfolioPosition> getCurrentPositions(Portfolio portfolio) {
        return positionRepository.findByPortfolio(portfolio);
    }

    /**
     * Пересчитывает среднюю цену приобретения для всех позиций на основе транзакций покупки
     * @param portfolio портфель
     */
    @Transactional
    public void recalculateAveragePrices(Portfolio portfolio) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolio(portfolio);
        List<PortfolioTransaction> transactions = transactionRepository.findByPortfolioOrderByTradeDateAsc(portfolio);

        // Группируем транзакции покупки по ISIN
        Map<String, List<PortfolioTransaction>> purchaseTransactionsByIsin = new HashMap<>();
        for (PortfolioTransaction transaction : transactions) {
            if ("Покупка".equals(transaction.getOperationType())) {
                purchaseTransactionsByIsin.computeIfAbsent(transaction.getIsin(), k -> new java.util.ArrayList<>())
                    .add(transaction);
            }
        }

        // Пересчитываем среднюю цену для каждой позиции
        for (PortfolioPosition position : positions) {
            List<PortfolioTransaction> purchases = purchaseTransactionsByIsin.get(position.getIsin());
            if (purchases == null || purchases.isEmpty()) {
                // Нет транзакций покупки - средняя цена остается как есть (или null)
                continue;
            }

            // Вычисляем средневзвешенную цену всех покупок
            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal totalQuantity = BigDecimal.ZERO;

            for (PortfolioTransaction purchase : purchases) {
                BigDecimal purchaseCost = purchase.getPrice().multiply(purchase.getQuantity());
                totalCost = totalCost.add(purchaseCost);
                totalQuantity = totalQuantity.add(purchase.getQuantity());
            }

            if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal averagePrice = totalCost.divide(totalQuantity, 6, RoundingMode.HALF_UP);
                position.setAveragePurchasePrice(averagePrice);
                positionRepository.save(position);
                log.debug("Пересчитана средняя цена для ISIN {}: {}", position.getIsin(), averagePrice);
            }
        }
    }

    /**
     * Обновляет среднюю цену приобретения для позиции
     * @param portfolio портфель
     * @param isin ISIN инструмента
     * @param averagePrice новая средняя цена
     */
    @Transactional
    public void updateAveragePrice(Portfolio portfolio, String isin, BigDecimal averagePrice) {
        Optional<PortfolioPosition> positionOpt = positionRepository.findByPortfolioAndIsin(portfolio, isin);
        if (positionOpt.isPresent()) {
            PortfolioPosition position = positionOpt.get();
            position.setAveragePurchasePrice(averagePrice);
            positionRepository.save(position);
            log.info("Обновлена средняя цена для ISIN {}: {}", isin, averagePrice);
        } else {
            log.warn("Позиция для ISIN {} не найдена", isin);
        }
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

    private static class PositionAccumulator {
        String isin;
        String securityType;
        BigDecimal quantity = BigDecimal.ZERO;
    }
}

