package com.stockintelligence.news;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Replaceable financial-news provider contract. Implementations must return
 * normalized articles; only title/description/url/source/timestamp are stored,
 * subject to each provider's licensing terms (no scraping, no full-text copying).
 */
public interface NewsProvider {

    List<NewsArticle> fetchNews(String symbol, String companyName, LocalDateTime from, LocalDateTime to);

    String providerName();
}
