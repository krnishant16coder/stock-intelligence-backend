package com.stockintelligence.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.common.TransientProviderException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Twelve Data market-data provider (alternative to Alpha Vantage).
 * Select with {@code app.market-data-provider=twelvedata}.
 * Exchange is passed as a separate parameter (NSE/BSE supported per vendor docs).
 */
@Component
@ConditionalOnProperty(name = "app.market-data-provider", havingValue = "twelvedata")
public class TwelveDataMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataMarketDataProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public TwelveDataMarketDataProvider(RestClient.Builder builder, ObjectMapper objectMapper,
                                        AppProperties properties) {
        this.restClient = builder.baseUrl(properties.getTwelveData().getBaseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "twelvedata";
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public MarketQuote getLatestQuote(String symbol, String exchange) {
        JsonNode root = get("/quote",
                "symbol", symbol.toUpperCase(), "interval", "1day", "exchange", exchange.toUpperCase());
        assertOk(root);
        return new MarketQuote(symbol.toUpperCase(), exchange.toUpperCase(),
                decimal(root, "close"), decimal(root, "open"), decimal(root, "high"), decimal(root, "low"),
                decimal(root, "previous_close"), longValue(root, "volume"),
                root.hasNonNull("timestamp")
                        ? java.time.Instant.ofEpochSecond(root.path("timestamp").asLong()) : null,
                providerName());
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public List<HistoricalPrice> getHistoricalPrices(String symbol, String exchange, LocalDate from, LocalDate to) {
        JsonNode root = get("/time_series",
                "symbol", symbol.toUpperCase(), "interval", "1day", "exchange", exchange.toUpperCase(),
                "outputsize", "120", "timezone", "Asia/Kolkata");
        assertOk(root);
        List<HistoricalPrice> out = new ArrayList<>();
        for (JsonNode v : root.path("values")) {
            LocalDate date = LocalDate.parse(v.path("datetime").asText().substring(0, 10));
            if ((from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))) {
                out.add(new HistoricalPrice(date, decimal(v, "open"), decimal(v, "high"),
                        decimal(v, "low"), decimal(v, "close"), longValue(v, "volume")));
            }
        }
        out.sort((a, b) -> a.tradingDate().compareTo(b.tradingDate()));
        if (out.isEmpty()) {
            throw new ExternalProviderException("TwelveData: no history for " + symbol + " (" + exchange + ")");
        }
        return out;
    }

    @Override
    public FundamentalData getFundamentals(String symbol, String exchange) {
        try {
            JsonNode root = get("/quote",
                    "symbol", symbol.toUpperCase(), "interval", "1day", "exchange", exchange.toUpperCase());
            assertOk(root);
            return new FundamentalData(symbol.toUpperCase(), exchange.toUpperCase(),
                    text(root, "currency"), decimal(root, "market_cap"), decimal(root, "pe_ratio"),
                    decimal(root, "eps"), decimal(root, "dividend_yield"),
                    decimal(root, "fifty_two_week_high"), decimal(root, "fifty_two_week_low"), providerName());
        } catch (Exception e) {
            log.info("TwelveData fundamentals unavailable for {} ({}): {}", symbol, exchange, e.getMessage());
            return null;
        }
    }

    private JsonNode get(String path, String... params) {
        try {
            String body = restClient.get().uri(uriBuilder -> {
                uriBuilder.path(path);
                for (int i = 0; i < params.length; i += 2) {
                    uriBuilder.queryParam(params[i], params[i + 1]);
                }
                uriBuilder.queryParam("apikey", properties.getTwelveData().getApiKey());
                return uriBuilder.build();
            }).retrieve().body(String.class);
            return objectMapper.readTree(body);
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("TwelveData request failed: " + e.getMessage(), e);
        } catch (ExternalProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalProviderException("TwelveData request failed: " + e.getMessage(), e);
        }
    }

    private static void assertOk(JsonNode root) {
        if ("error".equalsIgnoreCase(root.path("status").asText())) {
            throw new ExternalProviderException("TwelveData: " + root.path("message").asText("unknown error"));
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        if (v == null || v.isBlank() || v.equalsIgnoreCase("null") || v.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(JsonNode node, String field) {
        if (node.path(field).isNumber()) {
            return node.path(field).asLong();
        }
        BigDecimal d = decimal(node, field);
        return d == null ? null : d.longValue();
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return (v == null || v.isBlank() || v.equalsIgnoreCase("null")) ? null : v;
    }
}
