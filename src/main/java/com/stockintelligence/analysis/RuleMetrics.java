package com.stockintelligence.analysis;

import java.math.BigDecimal;

/** Deterministic, measurable metrics computed from price/volume history. */
public record RuleMetrics(BigDecimal latestPrice, Double dailyChangePct, Double weeklyChangePct,
                          Double monthlyChangePct, Double volumeRatio, boolean unusualVolume,
                          boolean sharpDailyMove, boolean weeklyDecline, boolean monthlyDecline,
                          int dataPoints, boolean insufficientData) {}
