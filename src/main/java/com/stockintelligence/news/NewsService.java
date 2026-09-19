package com.stockintelligence.news;

import com.stockintelligence.common.AppProperties;
import com.stockintelligence.stock.Stock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches news through the configured {@link NewsProvider} and persists
 * articles with URL-based deduplication.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final NewsProvider provider;
    private final NewsArticleRepository repository;
    private final AppProperties properties;

    public NewsService(List<NewsProvider> providers, NewsArticleRepository repository,
            AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
        String configured = properties.getNewsProvider() == null ? ""
                : properties.getNewsProvider().trim().toLowerCase();
        this.provider = providers.stream()
                .filter(p -> p.providerName() != null
                        && p.providerName().trim().equalsIgnoreCase(configured))
                .findFirst()
                .orElseGet(() -> {
                    // Prefer a real provider over the "none" fallback.
                    return providers.stream()
                            .filter(p -> !"none".equalsIgnoreCase(p.providerName()))
                            .findFirst().orElse(providers.get(0));
                });
        if (!this.provider.providerName().equalsIgnoreCase(configured)) {
            log.warn("app.news-provider='{}' unrecognized; using '{}' instead. Available: {}",
                    properties.getNewsProvider(), this.provider.providerName(),
                    providers.stream().map(NewsProvider::providerName).toList());
        } else {
            log.info("News provider: {}", this.provider.providerName());
        }
    }

    @Transactional
    public List<NewsArticle> fetchAndStore(Stock stock) {
        LocalDateTime to = LocalDateTime.now(ZONE);
        LocalDateTime from = to.minusDays(properties.getAnalysis().getNewsDays());
        List<NewsArticle> fetched = provider.fetchNews(stock.getSymbol(), stock.getCompanyName(), from, to);
        List<NewsArticle> fresh = new ArrayList<>();
        for (NewsArticle article : fetched) {
            if (article.getUrl() != null && repository.existsByUrl(article.getUrl())) {
                continue;
            }
            article.setStock(stock);
            fresh.add(repository.save(article));
        }
        log.info("Stored {} new articles for {} ({})", fresh.size(), stock.getSymbol(), stock.getExchange());
        return fresh;
    }

    @Transactional(readOnly = true)
    public List<NewsArticle> recentForStock(Long stockId, int days) {
        return repository.findByStockIdAndPublishedAtAfterOrderByPublishedAtDesc(
                stockId, java.time.Instant.now().minus(java.time.Duration.ofDays(days)));
    }
}
