package com.stockintelligence.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.common.TransientProviderException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Alpha Vantage market-data provider.
 *
 * <p>Free tier notes (verify against current plan before relying on limits):
 * daily request caps apply, so history is fetched with a single
 * {@code TIME_SERIES_DAILY} call per stock and fundamentals are best-effort.
 * Indian equities use the {@code .NSE}/{@code .BSE} suffix convention; symbols
 * already containing a suffix are passed through unchanged.
 */
@Component
@ConditionalOnProperty(name = "app.market-data-provider", havingValue = "alphavantage", matchIfMissing = true)
public class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageMarketDataProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public AlphaVantageMarketDataProvider(RestClient.Builder builder, ObjectMapper objectMapper,
                                          AppProperties properties) {
        this.restClient = builder.baseUrl(properties.getAlphaVantage().getBaseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "alphavantage";
    }

    /** Maps (symbol, exchange) to the vendor symbol, e.g. RELIANCE + NSE -> RELIANCE.NSE. */
    public static String toVendorSymbol(String symbol, String exchange) {
        if (symbol.contains(".")) {
            return symbol.toUpperCase();
        }
        String suffix = switch (exchange.toUpperCase()) {
            case "NSE" -> ".NSE";
            case "BSE" -> ".BSE";
            default -> "";
        };
        return symbol.toUpperCase() + suffix;
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public MarketQuote getLatestQuote(String symbol, String exchange) {
        JsonNode root = query(Map.of("function", "GLOBAL_QUOTE", "symbol", toVendorSymbol(symbol, exchange)));
        JsonNode quote = root.path("Global Quote");
        if (quote.isMissingNode() || quote.isEmpty()) {
            throw new ExternalProviderException("AlphaVantage: no quote for " + symbol + " (" + exchange + ")");
        }
        return new MarketQuote(symbol.toUpperCase(), exchange.toUpperCase(),
                decimal(quote, "05. price"), decimal(quote, "02. open"), decimal(quote, "03. high"),
                decimal(quote, "04. low"), decimal(quote, "08. previous close"),
                longValue(quote, "06. volume"),
                quote.path("07. latest trading day").asText(null) == null ? null
                        : LocalDate.parse(quote.path("07. latest trading day").asText()).atStartOfDay()
                                .toInstant(java.time.ZoneOffset.UTC),
                providerName());
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public List<HistoricalPrice> getHistoricalPrices(String symbol, String exchange, LocalDate from, LocalDate to) {
        JsonNode root = query(Map.of("function", "TIME_SERIES_DAILY", "symbol",
                toVendorSymbol(symbol, exchange), "outputsize", "compact"));
        JsonNode series = root.path("Time Series (Daily)");
        if (series.isMissingNode() || series.isEmpty()) {
            throw new ExternalProviderException("AlphaVantage: no daily history for " + symbol + " (" + exchange + ")");
        }
        List<HistoricalPrice> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = series.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            LocalDate date = LocalDate.parse(e.getKey());
            if ((from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))) {
                JsonNode day = e.getValue();
                out.add(new HistoricalPrice(date, decimal(day, "1. open"), decimal(day, "2. high"),
                        decimal(day, "3. low"), decimal(day, "4. close"), longValue(day, "5. volume")));
            }
        }
        out.sort((a, b) -> a.tradingDate().compareTo(b.tradingDate()));
        return out;
    }

    @Override
    public FundamentalData getFundamentals(String symbol, String exchange) {
        try {
            JsonNode root = query(Map.of("function", "OVERVIEW", "symbol", toVendorSymbol(symbol, exchange)));
            if (root.isEmpty() || !root.has("Symbol")) {
                log.info("AlphaVantage: no fundamentals for {} ({})", symbol, exchange);
                return null;
            }
            return new FundamentalData(symbol.toUpperCase(), exchange.toUpperCase(),
                    text(root, "Currency"), decimal(root, "MarketCapitalization"), decimal(root, "PERatio"),
                    decimal(root, "EPS"), decimal(root, "DividendYield"),
                    decimal(root, "52WeekHigh"), decimal(root, "52WeekLow"), providerName());
        } catch (ExternalProviderException e) {
            log.info("AlphaVantage fundamentals unavailable for {} ({}): {}", symbol, exchange, e.getMessage());
            return null;
        }
    }

    private JsonNode query(Map<String, String> params) {
        try {
            String body = restClient.get().uri(uriBuilder -> {
                uriBuilder.path("/query");
                params.forEach(uriBuilder::queryParam);
                uriBuilder.queryParam("apikey", properties.getAlphaVantage().getApiKey());
                return uriBuilder.build();
            }).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            assertNoApiError(root);
            return root;
        } catch (ExternalProviderException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("AlphaVantage request failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExternalProviderException("AlphaVantage request failed: " + e.getMessage(), e);
        }
    }

    private void assertNoApiError(JsonNode root) {
        if (root.has("Error Message")) {
            throw new ExternalProviderException("AlphaVantage: " + root.path("Error Message").asText());
        }
        // Free-tier rate limit / premium notices arrive as Note or Information.
        if (root.has("Note") || root.has("Information")) {
            String detail = root.has("Note") ? root.path("Note").asText() : root.path("Information").asText();
            throw new ExternalProviderException("AlphaVantage rate limit / plan notice: " + detail);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        if (v == null || v.isBlank() || v.equalsIgnoreCase("None") || v.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(JsonNode node, String field) {
        BigDecimal d = decimal(node, field);
        return d == null ? null : d.longValue();
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return (v == null || v.isBlank() || v.equalsIgnoreCase("None")) ? null : v;
    }
}
