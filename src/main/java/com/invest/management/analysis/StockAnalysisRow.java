package com.invest.management.analysis;

import java.math.BigDecimal;

public record StockAnalysisRow(
    StockAnalysis analysis,
    BigDecimal marketPrice,
    BigDecimal potentialGainPercent,
    BigDecimal potentialLossPercent,
    int priceDecimals,
    String supportDisplay,
    String resistanceDisplay,
    String inputStep,
    String expertTargetDisplay,
    String expertRecommendation,
    String expertRecommendationClass,
    BigDecimal expertForecastPercent
) {
}


