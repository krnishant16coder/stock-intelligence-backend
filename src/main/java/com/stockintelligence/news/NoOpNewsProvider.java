package com.stockintelligence.news;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Safety net: only active when no other {@link NewsProvider} matched the
 * {@code app.news-provider} conditional (e.g. typo, casing, trailing space
 * in the Azure App Setting). Returns no articles instead of crashing startup
 * with UnsatisfiedDependencyException.
 */
@Component
@ConditionalOnMissingBean(NewsProvider.class)
public class NoOpNewsProvider implements NewsProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpNewsProvider.class);

    @Override
    public String providerName() {
        return "none";
    }

    @Override
    public List<NewsArticle> fetchNews(String symbol, String companyName, LocalDateTime from,
            LocalDateTime to) {
        log.warn("No NewsProvider configured (app.news-provider unrecognized); returning no news for {}",
                symbol);
        return List.of();
    }
}
