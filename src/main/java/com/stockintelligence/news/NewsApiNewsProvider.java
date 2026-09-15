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
 * NewsAPI provider ({@code /v2/everything}). Query combines company name and
 * symbol for relevance; results sorted newest-first. Requires an API key.
 */
@Component
@ConditionalOnProperty(name = "app.news-provider", havingValue = "newsapi", matchIfMissing = true)
public class NewsApiNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(NewsApiNewsProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public NewsApiNewsProvider(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties properties) {
        this.restClient = builder.baseUrl(properties.getNewsApi().getBaseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "newsapi";
    }

    @Override
    @Retryable(retryFor = TransientProviderException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public List<NewsArticle> fetchNews(String symbol, String companyName, LocalDateTime from, LocalDateTime to) {
        String query = "\"" + companyName + "\" OR " + symbol + " stock";
        try {
            String body = restClient.get().uri(uriBuilder -> uriBuilder
                    .path("/v2/everything")
                    .queryParam("q", query)
                    .queryParam("language", "en")
                    .queryParam("sortBy", "publishedAt")
                    .queryParam("pageSize", properties.getAnalysis().getNewsMaxArticles())
                    .queryParam("from", from.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                    .queryParam("to", to.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                    .queryParam("apiKey", properties.getNewsApi().getApiKey())
                    .build()).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!"ok".equalsIgnoreCase(root.path("status").asText())) {
                throw new ExternalProviderException("NewsAPI: " + root.path("message").asText("unknown error"));
            }
            List<NewsArticle> out = new ArrayList<>();
            for (JsonNode a : root.path("articles")) {
                String url = a.path("url").asText(null);
                if (url == null || url.isBlank() || "[Removed]".equals(a.path("title").asText())) {
                    continue;
                }
                NewsArticle article = new NewsArticle();
                article.setTitle(a.path("title").asText(""));
                article.setSummary(a.path("description").asText(null));
                article.setSource(a.path("source").path("name").asText(null));
                article.setUrl(url);
                article.setDataSource(providerName());
                String published = a.path("publishedAt").asText(null);
                if (published != null && !published.isBlank()) {
                    try {
                        article.setPublishedAt(Instant.parse(published));
                    } catch (Exception e) {
                        log.debug("Unparseable date {}: {}", published, e.getMessage());
                    }
                }
                if (article.getPublishedAt() == null) {
                    article.setPublishedAt(from.toInstant(ZoneOffset.UTC));
                }
                out.add(article);
            }
            return out;
        } catch (ExternalProviderException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new TransientProviderException("NewsAPI request failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExternalProviderException("NewsAPI request failed: " + e.getMessage(), e);
        }
    }
}
