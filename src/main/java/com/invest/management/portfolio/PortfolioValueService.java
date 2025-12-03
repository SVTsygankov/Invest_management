package com.invest.management.portfolio;

import com.invest.management.moex.MoexStock;
import com.invest.management.moex.MoexStockRepository;
import com.invest.management.moex.bond.Bond;
import com.invest.management.moex.bond.BondRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioValueService {

    private final PortfolioPositionRepository positionRepository;
    private final MoexStockRepository stockRepository;
    private final BondRepository bondRepository;
    private final PortfolioCashMovementRepository cashMovementRepository;

    public PortfolioValueService(PortfolioPositionRepository positionRepository,
                                 MoexStockRepository stockRepository,
                                 BondRepository bondRepository,
                                 PortfolioCashMovementRepository cashMovementRepository) {
        this.positionRepository = positionRepository;
        this.stockRepository = stockRepository;
        this.bondRepository = bondRepository;
        this.cashMovementRepository = cashMovementRepository;
    }

    /**
     * Рассчитывает общую стоимость портфеля
     */
    public PortfolioValue calculatePortfolioValue(Portfolio portfolio) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolio(portfolio);
        
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal previousTotalValue = BigDecimal.ZERO;
        List<PositionValue> positionValues = new ArrayList<>();
        
        // Рассчитываем стоимость каждой позиции
        for (PortfolioPosition position : positions) {
            PositionValue posValue = calculatePositionValue(position);
            positionValues.add(posValue);
            
            if (posValue.getCurrentValue() != null) {
                totalValue = totalValue.add(posValue.getCurrentValue());
            }
            if (posValue.getPreviousValue() != null) {
                previousTotalValue = previousTotalValue.add(posValue.getPreviousValue());
            }
        }
        
        // Добавляем свободные денежные средства
        BigDecimal cashBalance = calculateCashBalance(portfolio);
        totalValue = totalValue.add(cashBalance);
        previousTotalValue = previousTotalValue.add(cashBalance); // Свободные средства не меняются
        
        // Рассчитываем изменение за день
        BigDecimal dailyChange = totalValue.subtract(previousTotalValue);
        BigDecimal dailyChangePercent = BigDecimal.ZERO;
        if (previousTotalValue.compareTo(BigDecimal.ZERO) > 0) {
            dailyChangePercent = dailyChange
                .divide(previousTotalValue, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }
        
        // Сортируем: сначала акции, потом облигации
        positionValues.sort(Comparator
            .comparing((PositionValue pv) -> !"STOCK".equals(pv.getSecurityType()))
            .thenComparing(PositionValue::getShortName));
        
        return new PortfolioValue(totalValue, previousTotalValue, dailyChange, dailyChangePercent, 
            positionValues, cashBalance);
    }

    private PositionValue calculatePositionValue(PortfolioPosition position) {
        String isin = position.getIsin();
        BigDecimal quantity = position.getQuantity();
        
        BigDecimal currentPrice = null;
        BigDecimal previousPrice = null;
        String shortName = isin; // По умолчанию используем ISIN
        Integer decimals = null; // Количество знаков после запятой для форматирования
        
        // ПРИОРИТЕТ 1: Используем last_known_price из ALOR (обновляется через AlorPriceUpdater)
        if (position.getLastKnownPrice() != null) {
            currentPrice = position.getLastKnownPrice();
        }
        
        // ПРИОРИТЕТ 2: Используем данные из MOEX справочников (через связи или поиск)
        // Получаем данные из связей или ищем по ISIN
        MoexStock stock = position.getMoexStock();
        Bond bond = position.getMoexBond();
        
        if (stock != null) {
            // Используем данные из связи
            if (currentPrice == null) {
                currentPrice = stock.getMarketprice();
            }
            previousPrice = stock.getPrevprice();
            shortName = stock.getShortname() != null ? stock.getShortname() : isin;
            decimals = stock.getDecimals();
        } else if (bond != null) {
            // Используем данные из связи
            if (currentPrice == null) {
                currentPrice = bond.getMarketprice();
            }
            previousPrice = bond.getPrevprice();
            shortName = bond.getShortname() != null ? bond.getShortname() : isin;
            decimals = bond.getDecimals();
        } else {
            // Fallback: ищем в справочниках по ISIN (если связи не установлены)
            if ("STOCK".equals(position.getSecurityType())) {
                Optional<MoexStock> stockOpt = stockRepository.findByIsin(isin);
                if (stockOpt.isPresent()) {
                    MoexStock foundStock = stockOpt.get();
                    if (currentPrice == null) {
                        currentPrice = foundStock.getMarketprice();
                    }
                    previousPrice = foundStock.getPrevprice();
                    shortName = foundStock.getShortname() != null ? foundStock.getShortname() : isin;
                    decimals = foundStock.getDecimals();
                }
            } else if ("BOND".equals(position.getSecurityType())) {
                Optional<Bond> bondOpt = bondRepository.findByIsin(isin);
                if (bondOpt.isPresent()) {
                    Bond foundBond = bondOpt.get();
                    if (currentPrice == null) {
                        currentPrice = foundBond.getMarketprice();
                    }
                    previousPrice = foundBond.getPrevprice();
                    shortName = foundBond.getShortname() != null ? foundBond.getShortname() : isin;
                    decimals = foundBond.getDecimals();
                }
            }
        }
        
        // Fallback: используем название из position.getSecurityName() 
        // (метод возвращает: MOEX справочник > название из отчета > ISIN)
        if (shortName.equals(isin)) {
            String securityName = position.getSecurityName();
            if (securityName != null && !securityName.isEmpty() && !securityName.equals(isin)) {
                shortName = securityName;
            }
        }
        
        BigDecimal currentValue = null;
        BigDecimal previousValue = null;
        BigDecimal dailyChange = null;
        BigDecimal dailyChangePercent = null;
        BigDecimal totalReturn = null;
        BigDecimal totalReturnPercent = null;
        
        if (currentPrice != null) {
            currentValue = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        }
        
        if (previousPrice != null) {
            previousValue = previousPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        }
        
        if (currentValue != null && previousValue != null) {
            dailyChange = currentValue.subtract(previousValue);
            if (previousValue.compareTo(BigDecimal.ZERO) > 0) {
                dailyChangePercent = dailyChange
                    .divide(previousValue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            }
        }
        
        // Расчет финансового результата за все время (текущая стоимость - средняя цена приобретения * количество)
        BigDecimal averagePurchasePrice = position.getAveragePurchasePrice();
        if (currentValue != null && averagePurchasePrice != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal purchaseValue = averagePurchasePrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            totalReturn = currentValue.subtract(purchaseValue);
            if (purchaseValue.compareTo(BigDecimal.ZERO) > 0) {
                totalReturnPercent = totalReturn
                    .divide(purchaseValue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            }
        }
        
        return new PositionValue(position, shortName, currentPrice, currentValue, 
            previousPrice, previousValue, dailyChange, dailyChangePercent, totalReturn, totalReturnPercent, decimals);
    }

    private BigDecimal calculateCashBalance(Portfolio portfolio) {
        return cashMovementRepository.calculateBalance(portfolio, "RUB");
    }

    // DTO для стоимости позиции
    public static class PositionValue {
        private final PortfolioPosition position;
        private final String shortName;
        private final BigDecimal currentPrice;
        private final BigDecimal currentValue;
        private final BigDecimal previousPrice;
        private final BigDecimal previousValue;
        private final BigDecimal dailyChange;
        private final BigDecimal dailyChangePercent;
        private final BigDecimal totalReturn;
        private final BigDecimal totalReturnPercent;
        private final Integer decimals;

        public PositionValue(PortfolioPosition position, String shortName,
                           BigDecimal currentPrice, BigDecimal currentValue,
                           BigDecimal previousPrice, BigDecimal previousValue,
                           BigDecimal dailyChange, BigDecimal dailyChangePercent,
                           BigDecimal totalReturn, BigDecimal totalReturnPercent,
                           Integer decimals) {
            this.position = position;
            this.shortName = shortName;
            this.currentPrice = currentPrice;
            this.currentValue = currentValue;
            this.previousPrice = previousPrice;
            this.previousValue = previousValue;
            this.dailyChange = dailyChange;
            this.dailyChangePercent = dailyChangePercent;
            this.totalReturn = totalReturn;
            this.totalReturnPercent = totalReturnPercent;
            this.decimals = decimals;
        }

        public PortfolioPosition getPosition() { return position; }
        public String getShortName() { return shortName; }
        public String getSecurityType() { return position.getSecurityType(); }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public BigDecimal getCurrentValue() { return currentValue; }
        public BigDecimal getPreviousPrice() { return previousPrice; }
        public BigDecimal getPreviousValue() { return previousValue; }
        public BigDecimal getDailyChange() { return dailyChange; }
        public BigDecimal getDailyChangePercent() { return dailyChangePercent; }
        public BigDecimal getTotalReturn() { return totalReturn; }
        public BigDecimal getTotalReturnPercent() { return totalReturnPercent; }
        public Integer getDecimals() { return decimals; }
    }

    // DTO для общей стоимости портфеля
    public static class PortfolioValue {
        private final BigDecimal totalValue;
        private final BigDecimal previousTotalValue;
        private final BigDecimal dailyChange;
        private final BigDecimal dailyChangePercent;
        private final List<PositionValue> positions;
        private final BigDecimal cashBalance;

        public PortfolioValue(BigDecimal totalValue, BigDecimal previousTotalValue,
                            BigDecimal dailyChange, BigDecimal dailyChangePercent,
                            List<PositionValue> positions, BigDecimal cashBalance) {
            this.totalValue = totalValue;
            this.previousTotalValue = previousTotalValue;
            this.dailyChange = dailyChange;
            this.dailyChangePercent = dailyChangePercent;
            this.positions = positions;
            this.cashBalance = cashBalance;
        }

        public BigDecimal getTotalValue() { return totalValue; }
        public BigDecimal getPreviousTotalValue() { return previousTotalValue; }
        public BigDecimal getDailyChange() { return dailyChange; }
        public BigDecimal getDailyChangePercent() { return dailyChangePercent; }
        public List<PositionValue> getPositions() { return positions; }
        public BigDecimal getCashBalance() { return cashBalance; }
    }
}

