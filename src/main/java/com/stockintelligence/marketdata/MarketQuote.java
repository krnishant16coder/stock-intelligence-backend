package com.stockintelligence.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/** Normalized latest quote, independent of vendor response shape. */
public record MarketQuote(String symbol, String exchange, BigDecimal price, BigDecimal open,
                          BigDecimal high, BigDecimal low, BigDecimal previousClose,
                          Long volume, Instant asOf, String dataSource) {}
