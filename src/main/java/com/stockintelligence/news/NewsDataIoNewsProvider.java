package com.stockintelligence.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.common.TransientProviderException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * NewsData.io provider (free tier friendly for Indian market).
 * Select with {@code app.news-provider=newsdata}.
 *
 * <p>Free plan (2026): 200 requests/day, 10 articles/request, 12h delay,
 * commercial use allowed. Query biased to India via
 * {@code country=in&language=en&category=business}. Free queries are limited
 * to 100 characters, so only the company name (truncated) is sent — the same
 * lesson as the GNews provider: strict {@code "Name SYMBOL stock"} AND queries
 * return zero articles for most NSE symbols.
 *
 * <p>Uses {@code GET /api/1/news} (not {@code /api/1/latest}, which is capped
 * at the last 48h). Date filtering is applied client-side since timeframe /
 * archive search is a paid feature.
 */
@Component
@ConditionalOnProperty(name = "app.news-provider", havingValue = "newsdata")
public class NewsDataIoNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(NewsDataIoNewsProvider.class);

    /** Free-plan query character limit. */
    static final int MAX_QUERY_CHARS = 100;
    /** NewsData.io {@code pubDate} format, e.g. {@code "2026-09-11 05:32:04"} (UTC). */
    private static final DateTimeFormatter PUBDATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public NewsDataIoNewsProvider(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties properties) {
        this.restClient = builder.baseUrl(properties.getNewsData().getBaseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "newsdata";
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public List<NewsArticle> fetchNews(String symbol, String companyName, LocalDateTime from, LocalDateTime to) {
        String query = buildQuery(symbol, companyName);
        try {
            String body = restClient.get().uri(uriBuilder -> uriBuilder
                    .path("/api/1/news")
                    .queryParam("apikey", properties.getNewsData().getApiKey())
                    .queryParam("q", query)
                    .queryParam("country", "in")
                    .queryParam("language", "en")
                    .queryParam("category", "business")
                    .build()).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!"success".equalsIgnoreCase(root.path("status").asText())) {
                throw new ExternalProviderException("NewsData.io: " + extractErrorMessage(root));
            }
            int max = properties.getAnalysis().getNewsMaxArticles();
            return mapArticles(root.path("results"), from, to, max);
        } catch (ExternalProviderException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("NewsData.io request failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExternalProviderException("NewsData.io request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build a free-tier-safe query: company name only, truncated to 100 chars.
     * Falls back to {@code "<symbol> stock"} when the name is blank.
     */
    static String buildQuery(String symbol, String companyName) {
        String base = (companyName != null && !companyName.isBlank())
                ? companyName.strip()
                : ((symbol != null ? symbol.strip() : "") + " stock").strip();
        if (base.length() > MAX_QUERY_CHARS) {
            base = base.substring(0, MAX_QUERY_CHARS);
        }
        return base;
    }

    /** Extract a readable message from NewsData.io error payloads. */
    static String extractErrorMessage(JsonNode root) {
        JsonNode results = root.path("results");
        if (results.isObject()) {
            String msg = results.path("message").asText(null);
            if (msg != null && !msg.isBlank()) {
                return msg;
            }
        }
        String msg = root.path("message").asText(null);
        return (msg == null || msg.isBlank()) ? "unknown error" : msg;
    }

    /**
     * Parse NewsData.io {@code pubDate}. Primary format is
     * {@code "yyyy-MM-dd HH:mm:ss"} in UTC; ISO-8601 instants are accepted as
     * fallback. Returns {@code null} when unparseable.
     */
    static Instant parsePubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        try {
            return PUBDATE_FORMAT.parse(value, Instant::from);
        } catch (Exception ignored) {
            // fall through to ISO-8601 attempt
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            log.debug("Unparseable NewsData.io date {}: {}", value, e.getMessage());
            return null;
        }
    }

    /** Normalize {@code results} array into entities with date-range filtering. */
    List<NewsArticle> mapArticles(JsonNode results, LocalDateTime from, LocalDateTime to, int max) {
        List<NewsArticle> out = new ArrayList<>();
        if (results == null || !results.isArray()) {
            return out;
        }
        Instant fromInstant = from.toInstant(ZoneOffset.UTC);
        Instant toInstant = to.plusDays(1).toInstant(ZoneOffset.UTC);
        for (JsonNode a : results) {
            if (out.size() >= max) {
                break;
            }
            String url = a.path("link").asText(null);
            if (url == null || url.isBlank()) {
                continue;
            }
            NewsArticle article = new NewsArticle();
            article.setTitle(a.path("title").asText(""));
            JsonNode desc = a.path("description");
            article.setSummary(desc.isNull() ? null : a.path("description").asText(null));
            String source = a.path("source_name").asText(null);
            if (source == null || source.isBlank() || "null".equalsIgnoreCase(source)) {
                source = a.path("source_id").asText(null);
            }
            article.setSource(source);
            article.setUrl(url);
            article.setDataSource(providerName());
            Instant published = parsePubDate(a.path("pubDate").asText(null));
            article.setPublishedAt(published != null ? published : fromInstant);
            if (article.getPublishedAt().isBefore(fromInstant)
                    || article.getPublishedAt().isAfter(toInstant)) {
                continue;
            }
            out.add(article);
        }
        return out;
    }
}
