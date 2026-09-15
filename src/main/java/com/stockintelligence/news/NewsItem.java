package com.stockintelligence.news;

import java.time.Instant;

/** Normalized news item returned by providers (distinct from the persisted entity). */
public record NewsItem(String title, String summary, String source, String url,
                       Instant publishedAt, String dataSource) {}
