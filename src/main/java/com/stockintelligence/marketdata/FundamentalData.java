package com.stockintelligence.marketdata;

import java.math.BigDecimal;

/** Basic fundamentals; all fields nullable since free tiers often omit them. */
public record FundamentalData(String symbol, String exchange, String currency,
                              BigDecimal marketCap, BigDecimal peRatio, BigDecimal eps,
                              BigDecimal dividendYield, BigDecimal fiftyTwoWeekHigh,
                              BigDecimal fiftyTwoWeekLow, String dataSource) {}
