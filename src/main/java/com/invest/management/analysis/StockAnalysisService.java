package com.invest.management.analysis;

import com.invest.management.moex.MoexStock;
import com.invest.management.moex.MoexStockRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockAnalysisService {

    private final StockAnalysisRepository analysisRepository;
    private final MoexStockRepository stockRepository;
    private final ExpertAssessmentRepository assessmentRepository;

    public StockAnalysisService(StockAnalysisRepository analysisRepository,
                                MoexStockRepository stockRepository,
                                ExpertAssessmentRepository assessmentRepository) {
        this.analysisRepository = analysisRepository;
        this.stockRepository = stockRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public List<StockAnalysisRow> getAllRows() {
        return getAllRows(null, null);
    }

    @Transactional(readOnly = true)
    public List<StockAnalysisRow> getAllRows(String sortBy, String sortDir) {
        List<StockAnalysis> analyses = analysisRepository.findAll(Sort.by(Sort.Direction.ASC, "stock.secid"));
        List<StockAnalysisRow> rows = new ArrayList<>(analyses.size());
        for (StockAnalysis analysis : analyses) {
            MoexStock stock = analysis.getStock();
            BigDecimal marketPrice = stock != null ? stock.getMarketprice() : null;
            int decimals = resolveDecimals(stock);
            BigDecimal target = analysis.getExpertTarget();
            BigDecimal forecast = calculateExpertForecastPercent(target, marketPrice);
            rows.add(new StockAnalysisRow(
                analysis,
                marketPrice,
                null, // potentialGain - не используется для stock_analysis
                null, // potentialLoss - не используется для stock_analysis
                decimals,
                "", // supportDisplay - не используется для stock_analysis
                "", // resistanceDisplay - не используется для stock_analysis
                buildStep(decimals),
                formatDecimal(target, decimals),
                analysis.getExpertRecommendation(),
                resolveRecommendationClass(analysis.getExpertRecommendation()),
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
    
    private int compareRows(StockAnalysisRow a, StockAnalysisRow b, String sortBy, boolean ascending) {
        int result;
        
        switch (sortBy.toLowerCase()) {
            case "secid":
                String secidA = a.analysis().getStock() != null ? a.analysis().getStock().getSecid() : "";
                String secidB = b.analysis().getStock() != null ? b.analysis().getStock().getSecid() : "";
                result = secidA.compareToIgnoreCase(secidB);
                break;
            case "shortname":
                String shortnameA = a.analysis().getStock() != null ? a.analysis().getStock().getShortname() : "";
                String shortnameB = b.analysis().getStock() != null ? b.analysis().getStock().getShortname() : "";
                result = shortnameA.compareToIgnoreCase(shortnameB);
                break;
            case "marketprice":
                result = compareBigDecimal(a.marketPrice(), b.marketPrice());
                break;
            case "experttarget":
                result = compareBigDecimal(a.analysis().getExpertTarget(), b.analysis().getExpertTarget());
                break;
            case "experttargetupdatedat":
                // Сортируем по expertTargetDate вместо expertTargetUpdatedAt
                java.time.LocalDate dateA = a.analysis().getExpertTargetDate();
                java.time.LocalDate dateB = b.analysis().getExpertTargetDate();
                if (dateA == null && dateB == null) result = 0;
                else if (dateA == null) result = -1;
                else if (dateB == null) result = 1;
                else result = dateA.compareTo(dateB);
                break;
            case "expertrecommendation":
                String recA = a.analysis().getExpertRecommendation() != null ? a.analysis().getExpertRecommendation() : "";
                String recB = b.analysis().getExpertRecommendation() != null ? b.analysis().getExpertRecommendation() : "";
                result = recA.compareToIgnoreCase(recB);
                break;
            case "expertforecastpercent":
                result = compareBigDecimal(a.expertForecastPercent(), b.expertForecastPercent());
                break;
            default:
                // По умолчанию сортируем по тикеру
                String defaultSecidA = a.analysis().getStock() != null ? a.analysis().getStock().getSecid() : "";
                String defaultSecidB = b.analysis().getStock() != null ? b.analysis().getStock().getSecid() : "";
                result = defaultSecidA.compareToIgnoreCase(defaultSecidB);
                break;
        }
        
        return ascending ? result : -result;
    }
    
    private int compareBigDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    @Transactional(readOnly = true)
    public List<MoexStock> getAvailableStocks() {
        List<StockAnalysis> analyses = analysisRepository.findAll(Sort.by(Sort.Direction.ASC, "stock.secid"));
        List<MoexStock> allStocks = stockRepository.findAll(Sort.by(Sort.Direction.ASC, "secid"));
        if (analyses.isEmpty()) {
            return allStocks;
        }
        return allStocks.stream()
            .filter(stock -> analyses.stream().noneMatch(analysis -> 
                analysis.getStock() != null && analysis.getStock().getId().equals(stock.getId())))
            .toList();
    }

    @Transactional
    public void addStock(MoexStock stock) {
        analysisRepository.findByStock(stock)
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Эта акция уже добавлена в анализ.");
            });
        StockAnalysis analysis = new StockAnalysis();
        analysis.setStock(stock);
        analysisRepository.save(analysis);
    }

    /**
     * Добавляет новую экспертной оценку для анализа
     */
    @Transactional
    public void addExpertAssessment(Long analysisId,
                                    BigDecimal target, String recommendation,
                                    java.time.LocalDate targetDate) {
        StockAnalysis analysis = analysisRepository.findById(analysisId)
            .orElseThrow(() -> new IllegalArgumentException("Анализ не найден"));
        
        ExpertAssessment assessment = new ExpertAssessment();
        assessment.setStockAnalysis(analysis);
        assessment.setExpertTarget(target);
        assessment.setExpertRecommendation(normalizeRecommendation(recommendation));
        assessment.setExpertTargetDate(targetDate != null ? targetDate : java.time.LocalDate.now());
        
        ExpertAssessment saved = assessmentRepository.save(assessment);
        
        // Обновляем ссылку на последнюю оценку
        analysis.setCurrentExpertAssessment(saved);
        analysisRepository.save(analysis);
    }

    /**
     * Обновляет существующую экспертной оценку
     */
    @Transactional
    public void updateExpertAssessment(Long assessmentId,
                                       BigDecimal target, String recommendation,
                                       java.time.LocalDate targetDate) {
        ExpertAssessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Экспертная оценка не найдена"));
        
        assessment.setExpertTarget(target);
        assessment.setExpertRecommendation(normalizeRecommendation(recommendation));
        if (targetDate != null) {
            assessment.setExpertTargetDate(targetDate);
        }
        
        assessmentRepository.save(assessment);
        
        // Если это текущая оценка, обновляем ссылку
        StockAnalysis analysis = assessment.getStockAnalysis();
        if (analysis != null && analysis.getCurrentExpertAssessment() != null 
            && analysis.getCurrentExpertAssessment().getId().equals(assessmentId)) {
            // Ссылка уже установлена, ничего не делаем
        }
    }

    /**
     * Удаляет экспертной оценку
     */
    @Transactional
    public void deleteExpertAssessment(Long assessmentId) {
        ExpertAssessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Экспертная оценка не найдена"));
        
        StockAnalysis analysis = assessment.getStockAnalysis();
        
        // Если это текущая оценка, нужно найти предыдущую или установить null
        if (analysis != null && analysis.getCurrentExpertAssessment() != null 
            && analysis.getCurrentExpertAssessment().getId().equals(assessmentId)) {
            List<ExpertAssessment> allAssessments = assessmentRepository
                .findByStockAnalysisOrderByExpertTargetDateDescCreatedAtDesc(analysis);
            ExpertAssessment newCurrent = allAssessments.stream()
                .filter(a -> !a.getId().equals(assessmentId))
                .findFirst()
                .orElse(null);
            analysis.setCurrentExpertAssessment(newCurrent);
            analysisRepository.save(analysis);
        }
        
        assessmentRepository.delete(assessment);
    }

    /**
     * Получает историю экспертных оценок для анализа
     */
    @Transactional(readOnly = true)
    public List<ExpertAssessmentDto> getExpertAssessmentHistory(Long analysisId) {
        StockAnalysis analysis = analysisRepository.findById(analysisId)
            .orElseThrow(() -> new IllegalArgumentException("Анализ не найден"));
        
        List<ExpertAssessment> assessments = assessmentRepository
            .findByStockAnalysisOrderByExpertTargetDateDescCreatedAtDesc(analysis);
        
        return assessments.stream()
            .map(this::toDto)
            .toList();
    }

    private ExpertAssessmentDto toDto(ExpertAssessment assessment) {
        ExpertAssessmentDto dto = new ExpertAssessmentDto();
        dto.setId(assessment.getId());
        dto.setExpertTarget(assessment.getExpertTarget());
        dto.setExpertRecommendation(assessment.getExpertRecommendation());
        dto.setExpertTargetDate(assessment.getExpertTargetDate());
        dto.setCreatedAt(assessment.getCreatedAt());
        return dto;
    }

    @Transactional
    public void delete(Long analysisId) {
        analysisRepository.deleteById(analysisId);
    }

    private BigDecimal calculateExpertForecastPercent(BigDecimal target, BigDecimal marketPrice) {
        if (target == null || marketPrice == null || marketPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return target.subtract(marketPrice)
            .divide(marketPrice, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
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

    private String normalizeRecommendation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return switch (trimmed) {
            case "покупать" -> "Покупать";
            case "держать" -> "Держать";
            case "продавать" -> "Продавать";
            default -> null;
        };
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
