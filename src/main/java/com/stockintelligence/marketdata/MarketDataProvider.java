package com.stockintelligence.marketdata;

import java.time.LocalDate;
import java.util.List;

/**
 * Replaceable market-data provider contract. Business logic must depend only
 * on this interface, never on a vendor SDK or response shape.
 */
public interface MarketDataProvider {

    MarketQuote getLatestQuote(String symbol, String exchange);

    List<HistoricalPrice> getHistoricalPrices(String symbol, String exchange, LocalDate from, LocalDate to);

    FundamentalData getFundamentals(String symbol, String exchange);

    /** Stable key used for persistence ({@code data_source}) and provider selection. */
    String providerName();
}
