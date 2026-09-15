package com.stockintelligence.news;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    boolean existsByUrl(String url);
    List<NewsArticle> findByStockIdAndPublishedAtAfterOrderByPublishedAtDesc(Long stockId, Instant after);
    List<NewsArticle> findByStockIdOrderByPublishedAtDesc(Long stockId);
}
