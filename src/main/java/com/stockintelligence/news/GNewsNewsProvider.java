package com.stockintelligence.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.common.TransientProviderException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * GNews provider (alternative to NewsAPI). Select with
 * {@code app.news-provider=gnews}. Country biased to India for NSE/BSE relevance.
 */
@Component
@ConditionalOnProperty(name = "app.news-provider", havingValue = "gnews")
public class GNewsNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(GNewsNewsProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public GNewsNewsProvider(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties properties) {
        this.restClient = builder.baseUrl(properties.getGnews().getBaseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "gnews";
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public List<NewsArticle> fetchNews(String symbol, String companyName, LocalDateTime from, LocalDateTime to) {
        try {
            String body = restClient.get().uri(uriBuilder -> uriBuilder
                    .path("/api/v4/search")
                    // Company name only: strict multi-word AND queries
                    // (e.g. "HDFC Bank HDFCBANK stock") return zero articles
                    // for most symbols, while the name alone matches thousands.
                    .queryParam("q", (companyName != null && !companyName.isBlank())
                            ? companyName : symbol + " stock")
                    .queryParam("lang", "en")
                    .queryParam("country", "in")
                    .queryParam("max", Math.min(properties.getAnalysis().getNewsMaxArticles(), 100))
                    .queryParam("sortby", "publishedAt")
                    .queryParam("apikey", properties.getGnews().getApiKey())
                    .build()).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (root.has("errors")) {
                throw new ExternalProviderException("GNews: " + root.path("errors").toString());
            }
            List<NewsArticle> out = new ArrayList<>();
            for (JsonNode a : root.path("articles")) {
                String url = a.path("url").asText(null);
                if (url == null || url.isBlank()) {
                    continue;
                }
                NewsArticle article = new NewsArticle();
                article.setTitle(a.path("title").asText(""));
                article.setSummary(a.path("description").asText(null));
                article.setSource(a.path("source").path("name").asText(null));
                article.setUrl(url);
                article.setDataSource(providerName());
                try {
                    article.setPublishedAt(Instant.parse(a.path("publishedAt").asText()));
                } catch (Exception e) {
                    log.debug("Unparseable GNews date: {}", e.getMessage());
                    article.setPublishedAt(from.toInstant(ZoneOffset.UTC));
                }
                Instant published = article.getPublishedAt();
                if (published.isBefore(from.toInstant(ZoneOffset.UTC))
                        || published.isAfter(to.plusDays(1).toInstant(ZoneOffset.UTC))) {
                    continue;
                }
                out.add(article);
            }
            return out;
        } catch (ExternalProviderException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("GNews request failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExternalProviderException("GNews request failed: " + e.getMessage(), e);
        }
    }
}
