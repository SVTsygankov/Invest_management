package com.invest.management.analysis;

import com.invest.management.moex.MoexStock;
import com.invest.management.moex.MoexStockRepository;
import com.invest.management.user.AppUser;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAnalysisService {

    private final UserAnalysisRepository userAnalysisRepository;
    private final StockAnalysisRepository stockAnalysisRepository;
    private final MoexStockRepository stockRepository;

    public UserAnalysisService(UserAnalysisRepository userAnalysisRepository,
                              StockAnalysisRepository stockAnalysisRepository,
                              MoexStockRepository stockRepository) {
        this.userAnalysisRepository = userAnalysisRepository;
        this.stockAnalysisRepository = stockAnalysisRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    public List<UserAnalysisRow> getRowsForUser(AppUser user) {
        return getRowsForUser(user, null, null);
    }

    @Transactional(readOnly = true)
    public List<UserAnalysisRow> getRowsForUser(AppUser user, String sortBy, String sortDir) {
        List<UserAnalysis> userAnalyses = userAnalysisRepository.findByUserOrderByStockAnalysisStockSecidAsc(user);
        List<UserAnalysisRow> rows = new ArrayList<>(userAnalyses.size());
        
        for (UserAnalysis userAnalysis : userAnalyses) {
            StockAnalysis stockAnalysis = userAnalysis.getStockAnalysis();
            MoexStock stock = stockAnalysis != null ? stockAnalysis.getStock() : null;
            BigDecimal marketPrice = stock != null ? stock.getMarketprice() : null;
            BigDecimal potentialGain = calculatePotentialGainPercent(userAnalysis.getSupport(), marketPrice);
            BigDecimal potentialLoss = calculatePotentialLossPercent(userAnalysis.getResistance(), marketPrice);
            int decimals = resolveDecimals(stock);
            BigDecimal target = stockAnalysis != null ? stockAnalysis.getExpertTarget() : null;
            BigDecimal forecast = calculateExpertForecastPercent(target, marketPrice);
            
            rows.add(new UserAnalysisRow(
                userAnalysis,
                marketPrice,
                potentialGain,
                potentialLoss,
                decimals,
                formatDecimal(userAnalysis.getSupport(), decimals),
                formatDecimal(userAnalysis.getResistance(), decimals),
                buildStep(decimals),
                formatDecimal(target, decimals),
                stockAnalysis != null ? stockAnalysis.getExpertRecommendation() : null,
                resolveRecommendationClass(stockAnalysis != null ? stockAnalysis.getExpertRecommendation() : null),
                forecast != null ? forecast.setScale(2, RoundingMode.HALF_UP) : null
            ));
        }
        
        // Применяем сортировку, если указана
        if (sortBy != null && !sortBy.isBlank()) {
            boolean ascending = !"desc".equalsIgnoreCase(sortDir);
            rows.sort((a, b) -> compareRows(a, b, sortBy, ascending));
        }
        
        return rows;
    }

    private int compareRows(UserAnalysisRow a, UserAnalysisRow b, String sortBy, boolean ascending) {
        int result = 0;
        StockAnalysis saA = a.userAnalysis().getStockAnalysis();
        StockAnalysis saB = b.userAnalysis().getStockAnalysis();
        MoexStock stockA = saA != null ? saA.getStock() : null;
        MoexStock stockB = saB != null ? saB.getStock() : null;
        
        switch (sortBy.toLowerCase()) {
            case "secid":
                String secidA = stockA != null ? stockA.getSecid() : "";
                String secidB = stockB != null ? stockB.getSecid() : "";
                result = secidA.compareToIgnoreCase(secidB);
                break;
            case "shortname":
                String shortnameA = stockA != null ? stockA.getShortname() : "";
                String shortnameB = stockB != null ? stockB.getShortname() : "";
                result = shortnameA.compareToIgnoreCase(shortnameB);
                break;
            case "support":
                result = compareBigDecimal(a.userAnalysis().getSupport(), b.userAnalysis().getSupport());
                break;
            case "supportupdatedat":
                result = compareOffsetDateTime(a.userAnalysis().getSupportUpdatedAt(), b.userAnalysis().getSupportUpdatedAt());
                break;
            case "resistance":
                result = compareBigDecimal(a.userAnalysis().getResistance(), b.userAnalysis().getResistance());
                break;
            case "resistanceupdatedat":
                result = compareOffsetDateTime(a.userAnalysis().getResistanceUpdatedAt(), b.userAnalysis().getResistanceUpdatedAt());
                break;
            case "marketprice":
                result = compareBigDecimal(a.marketPrice(), b.marketPrice());
                break;
            case "potentialgainpercent":
                result = compareBigDecimal(a.potentialGainPercent(), b.potentialGainPercent());
                break;
            case "potentiallosspercent":
                result = compareBigDecimal(a.potentialLossPercent(), b.potentialLossPercent());
                break;
            case "experttarget":
                result = compareBigDecimal(saA != null ? saA.getExpertTarget() : null, 
                                         saB != null ? saB.getExpertTarget() : null);
                break;
            case "experttargetupdatedat":
                result = compareOffsetDateTime(saA != null ? saA.getExpertTargetUpdatedAt() : null,
                                              saB != null ? saB.getExpertTargetUpdatedAt() : null);
                break;
            case "expertrecommendation":
                String recA = saA != null && saA.getExpertRecommendation() != null ? saA.getExpertRecommendation() : "";
                String recB = saB != null && saB.getExpertRecommendation() != null ? saB.getExpertRecommendation() : "";
                result = recA.compareToIgnoreCase(recB);
                break;
            case "expertforecastpercent":
                result = compareBigDecimal(a.expertForecastPercent(), b.expertForecastPercent());
                break;
            default:
                // По умолчанию сортируем по тикеру
                String defaultSecidA = stockA != null ? stockA.getSecid() : "";
                String defaultSecidB = stockB != null ? stockB.getSecid() : "";
                result = defaultSecidA.compareToIgnoreCase(defaultSecidB);
                break;
        }
        
        return ascending ? result : -result;
    }

    @Transactional(readOnly = true)
    public List<StockAnalysis> getAvailableStockAnalyses(AppUser user) {
        List<UserAnalysis> userAnalyses = userAnalysisRepository.findByUserOrderByStockAnalysisStockSecidAsc(user);
        Set<Long> usedStockAnalysisIds = userAnalyses.stream()
            .map(ua -> ua.getStockAnalysis().getId())
            .collect(Collectors.toSet());
        
        List<StockAnalysis> allStockAnalyses = stockAnalysisRepository.findAll(Sort.by(Sort.Direction.ASC, "stock.secid"));
        if (usedStockAnalysisIds.isEmpty()) {
            return allStockAnalyses;
        }
        return allStockAnalyses.stream()
            .filter(sa -> !usedStockAnalysisIds.contains(sa.getId()))
            .toList();
    }

    @Transactional
    public void addStockAnalyses(AppUser user, List<Long> stockAnalysisIds) {
        for (Long stockAnalysisId : stockAnalysisIds) {
            StockAnalysis stockAnalysis = stockAnalysisRepository.findById(stockAnalysisId)
                .orElseThrow(() -> new IllegalArgumentException("Stock analysis not found: " + stockAnalysisId));
            
            // Проверяем, не добавлена ли уже эта запись
            userAnalysisRepository.findByUserAndStockAnalysis(user, stockAnalysis)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Эта акция уже добавлена в анализ: " + 
                        (stockAnalysis.getStock() != null ? stockAnalysis.getStock().getSecid() : stockAnalysisId));
                });
            
            UserAnalysis userAnalysis = new UserAnalysis();
            userAnalysis.setUser(user);
            userAnalysis.setStockAnalysis(stockAnalysis);
            userAnalysisRepository.save(userAnalysis);
        }
    }

    @Transactional
    public void updateLevels(AppUser user,
                             Map<Long, String> supportUpdates,
                             Map<Long, String> resistanceUpdates) {
        Set<Long> ids = new HashSet<>();
        if (supportUpdates != null) {
            ids.addAll(supportUpdates.keySet());
        }
        if (resistanceUpdates != null) {
            ids.addAll(resistanceUpdates.keySet());
        }
        if (ids.isEmpty()) {
            return;
        }
        
        List<UserAnalysis> userAnalyses = new ArrayList<>();
        for (Long id : ids) {
            userAnalysisRepository.findByIdAndUser(id, user)
                .ifPresent(userAnalyses::add);
        }
        
        List<UserAnalysis> toSave = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        
        for (UserAnalysis userAnalysis : userAnalyses) {
            boolean updated = false;
            StockAnalysis stockAnalysis = userAnalysis.getStockAnalysis();
            MoexStock stock = stockAnalysis != null ? stockAnalysis.getStock() : null;
            int decimals = resolveDecimals(stock);
            
            BigDecimal newSupport = parseDecimal(supportUpdates != null ? supportUpdates.get(userAnalysis.getId()) : null, decimals);
            if (!compareDecimal(userAnalysis.getSupport(), newSupport)) {
                userAnalysis.setSupport(newSupport);
                userAnalysis.setSupportUpdatedAt(newSupport != null ? now : null);
                updated = true;
            }
            
            BigDecimal newResistance = parseDecimal(resistanceUpdates != null ? resistanceUpdates.get(userAnalysis.getId()) : null, decimals);
            if (!compareDecimal(userAnalysis.getResistance(), newResistance)) {
                userAnalysis.setResistance(newResistance);
                userAnalysis.setResistanceUpdatedAt(newResistance != null ? now : null);
                updated = true;
            }
            
            if (updated) {
                toSave.add(userAnalysis);
            }
        }
        
        if (!toSave.isEmpty()) {
            userAnalysisRepository.saveAll(toSave);
        }
    }

    @Transactional
    public void delete(AppUser user, Long userAnalysisId) {
        userAnalysisRepository.deleteByIdAndUser(userAnalysisId, user);
    }

    private int compareBigDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private int compareOffsetDateTime(OffsetDateTime a, OffsetDateTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private BigDecimal calculatePotentialGainPercent(BigDecimal support, BigDecimal marketPrice) {
        if (support == null || marketPrice == null || marketPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return support.subtract(marketPrice)
            .divide(marketPrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePotentialLossPercent(BigDecimal resistance, BigDecimal marketPrice) {
        if (resistance == null || marketPrice == null || marketPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return marketPrice.subtract(resistance)
            .divide(marketPrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateExpertForecastPercent(BigDecimal target, BigDecimal marketPrice) {
        if (target == null || marketPrice == null || marketPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return target.subtract(marketPrice)
            .divide(marketPrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal parseDecimal(String value, int decimals) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            int scale = Math.max(decimals, 0);
            return parsed.setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректное числовое значение: " + value);
        }
    }

    private boolean compareDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private String formatDecimal(BigDecimal value, int decimals) {
        if (value == null) {
            return "";
        }
        int scale = Math.max(decimals, 0);
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String buildStep(int decimals) {
        if (decimals <= 0) {
            return "1";
        }
        StringBuilder sb = new StringBuilder("0.");
        for (int i = 1; i < decimals; i++) {
            sb.append('0');
        }
        sb.append('1');
        return sb.toString();
    }

    private int resolveDecimals(MoexStock stock) {
        if (stock == null || stock.getDecimals() == null) {
            return 2;
        }
        return Math.max(stock.getDecimals(), 0);
    }

    private String resolveRecommendationClass(String recommendation) {
        if (recommendation == null) {
            return "recommendation-none";
        }
        return switch (recommendation) {
            case "Покупать" -> "recommendation-buy";
            case "Держать" -> "recommendation-hold";
            case "Продавать" -> "recommendation-sell";
            default -> "recommendation-none";
        };
    }
}

