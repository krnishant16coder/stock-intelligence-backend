package com.stockintelligence.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.common.TransientProviderException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Yahoo Finance chart-API provider (no API key, generous rate limits).
 *
 * <p>Covers NSE ({@code .NS}), BSE ({@code .BO}) and US listings. Always
 * active: {@link MarketDataService} tries Yahoo first for history/quote so
 * every stock gets real data even when the keyed provider
 * (Alpha Vantage free: ~25 req/day, 3 req/stock) is rate-limited.
 */
@Component
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceMarketDataProvider.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) stock-intelligence/1.0";

    private static final List<String> HOSTS = List.of(
            "https://query1.finance.yahoo.com", "https://query2.finance.yahoo.com");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public YahooFinanceMarketDataProvider(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.defaultHeader("User-Agent", UA).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "yahoo";
    }

    /** Maps (symbol, exchange) to the Yahoo symbol, e.g. RELIANCE + NSE -> RELIANCE.NS. */
    public static String toVendorSymbol(String symbol, String exchange) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        String sym = symbol.toUpperCase().trim();
        if (sym.contains(".") || sym.contains("^") || sym.contains("=")) {
            return sym;
        }
        String ex = exchange == null ? "" : exchange.toUpperCase().trim();
        if (ex.contains("NSE") || ex.equals("NS") || ex.contains("NATIONAL STOCK")) {
            return sym + ".NS";
        }
        if (ex.contains("BSE") || ex.equals("BO") || ex.contains("BOMBAY STOCK")) {
            return sym + ".BO";
        }
        return sym;
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 2,
            backoff = @Backoff(delay = 1500, multiplier = 2))
    public MarketQuote getLatestQuote(String symbol, String exchange) {
        LocalDate to = LocalDate.now(ZONE);
        List<HistoricalPrice> bars = getHistoricalPrices(symbol, exchange, to.minusDays(14), to);
        HistoricalPrice last = bars.get(bars.size() - 1);
        BigDecimal prevClose = bars.size() > 1 ? bars.get(bars.size() - 2).close() : null;
        String vendor = toVendorSymbol(symbol, exchange);
        JsonNode meta = fetchMeta(vendor, to.minusDays(14), to);
        String currency = meta == null ? null : text(meta, "currency");
        return new MarketQuote(symbol.toUpperCase(), exchange == null ? "" : exchange.toUpperCase(),
                last.close(), last.open(), last.high(), last.low(), prevClose, last.volume(),
                last.tradingDate().atStartOfDay(ZONE).toInstant(), providerName());
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 2,
            backoff = @Backoff(delay = 1500, multiplier = 2))
    public List<HistoricalPrice> getHistoricalPrices(String symbol, String exchange,
                                                     LocalDate from, LocalDate to) {
        JsonNode root = fetchChart(toVendorSymbol(symbol, exchange), from, to);
        List<HistoricalPrice> out = parseChart(root, from, to);
        if (out.isEmpty()) {
            throw new ExternalProviderException("Yahoo: no history for " + symbol + " (" + exchange + ")");
        }
        return out;
    }

    @Override
    public FundamentalData getFundamentals(String symbol, String exchange) {
        try {
            LocalDate to = LocalDate.now(ZONE);
            JsonNode meta = fetchMeta(toVendorSymbol(symbol, exchange), to.minusDays(40), to);
            if (meta == null || meta.isMissingNode()) {
                return null;
            }
            // Chart meta has no P/E or EPS, but 52-week range + currency are real
            // data points — better than the null Alpha Vantage returns for India.
            BigDecimal hi52 = decimal(meta, "fiftyTwoWeekHigh");
            BigDecimal lo52 = decimal(meta, "fiftyTwoWeekLow");
            String currency = text(meta, "currency");
            if (hi52 == null && lo52 == null && currency == null) {
                return null;
            }
            return new FundamentalData(symbol.toUpperCase(),
                    exchange == null ? "" : exchange.toUpperCase(), currency,
                    null, null, null, null, hi52, lo52, providerName());
        } catch (Exception e) {
            log.info("Yahoo fundamentals unavailable for {} ({}): {}", symbol, exchange, e.getMessage());
            return null;
        }
    }

    private JsonNode fetchMeta(String vendorSymbol, LocalDate from, LocalDate to) {
        try {
            return fetchChart(vendorSymbol, from, to).path("chart").path("result").path(0).path("meta");
        } catch (Exception e) {
            log.debug("Yahoo meta fetch failed for {}: {}", vendorSymbol, e.getMessage());
            return null;
        }
    }

    private JsonNode fetchChart(String vendorSymbol, LocalDate from, LocalDate to) {
        long period1 = from == null ? LocalDate.now(ZONE).minusDays(120).atStartOfDay(ZONE).toEpochSecond()
                : from.atStartOfDay(ZONE).toEpochSecond();
        long period2 = (to == null ? LocalDate.now(ZONE) : to).plusDays(1).atStartOfDay(ZONE).toEpochSecond();
        ExternalProviderException lastError = null;
        for (String host : HOSTS) {
            try {
                String body = restClient.get().uri(uriBuilder -> uriBuilder
                        .scheme("https").host(host.replace("https://", ""))
                        .path("/v8/finance/chart/" + vendorSymbol)
                        .queryParam("period1", period1)
                        .queryParam("period2", period2)
                        .queryParam("interval", "1d")
                        .queryParam("events", "div|split")
                        .build()).retrieve().body(String.class);
                JsonNode root = objectMapper.readTree(body);
                JsonNode error = root.path("chart").path("error");
                if (!error.isMissingNode() && !error.isNull()) {
                    throw new ExternalProviderException(
                            "Yahoo: " + error.path("description").asText(error.toString()));
                }
                return root;
            } catch (ExternalProviderException e) {
                lastError = e;
            } catch (ResourceAccessException e) {
                throw new TransientProviderException("Yahoo request failed: " + e.getMessage(), e);
            } catch (Exception e) {
                lastError = new ExternalProviderException("Yahoo request failed: " + e.getMessage(), e);
            }
        }
        throw lastError != null ? lastError
                : new ExternalProviderException("Yahoo: chart fetch failed for " + vendorSymbol);
    }

    /** Parses a chart-API payload into daily bars. Exposed for tests. */
    public static List<HistoricalPrice> parseChart(JsonNode root, LocalDate from, LocalDate to) {
        List<HistoricalPrice> out = new ArrayList<>();
        JsonNode result = root.path("chart").path("result").path(0);
        if (result.isMissingNode()) {
            return out;
        }
        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote").path(0);
        JsonNode adj = result.path("indicators").path("adjclose").path(0).path("adjclose");
        for (int i = 0; i < timestamps.size(); i++) {
            long epoch = timestamps.path(i).asLong(0);
            if (epoch <= 0) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(epoch).atZone(ZONE).toLocalDate();
            if ((from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) {
                continue;
            }
            BigDecimal close = decimalAt(quote, "close", i);
            if (close == null && adj.isArray() && i < adj.size()) {
                close = decimalNode(adj.path(i));
            }
            if (close == null) {
                continue; // weekends/holidays have null bars
            }
            out.add(new HistoricalPrice(date, decimalAt(quote, "open", i),
                    decimalAt(quote, "high", i), decimalAt(quote, "low", i),
                    close, longAt(quote, "volume", i)));
        }
        out.sort((a, b) -> a.tradingDate().compareTo(b.tradingDate()));
        return out;
    }

    private static BigDecimal decimalAt(JsonNode parent, String field, int idx) {
        JsonNode arr = parent.path(field);
        if (!arr.isArray() || idx >= arr.size()) {
            return null;
        }
        return decimalNode(arr.path(idx));
    }

    private static Long longAt(JsonNode parent, String field, int idx) {
        JsonNode arr = parent.path(field);
        if (!arr.isArray() || idx >= arr.size() || arr.path(idx).isNull()) {
            return null;
        }
        try {
            return arr.path(idx).asLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return decimalNode(node.path(field));
    }

    private static BigDecimal decimalNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return (v == null || v.isBlank() || v.equalsIgnoreCase("null")) ? null : v;
    }
}
