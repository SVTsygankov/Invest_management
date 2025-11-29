package com.invest.management.analysis;

import java.math.BigDecimal;

/**
 * DTO для отображения строки user_analysis в таблице
 */
public record UserAnalysisRow(
    UserAnalysis userAnalysis,
    BigDecimal marketPrice,
    BigDecimal potentialGainPercent,
    BigDecimal potentialLossPercent,
    int priceDecimals,
    String supportDisplay,
    String resistanceDisplay,
    String inputStep,
    String expertTargetDisplay,
    String expertRecommendation,
    String recommendationClass,
    BigDecimal expertForecastPercent
) {
}

