package com.stockintelligence.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPrice(LocalDate tradingDate, BigDecimal open, BigDecimal high,
                              BigDecimal low, BigDecimal close, Long volume) {}
