package com.stockintelligence.watchlist;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistStockRepository extends JpaRepository<WatchlistStock, Long> {
    List<WatchlistStock> findByWatchlistId(Long watchlistId);
    boolean existsByWatchlistIdAndStockId(Long watchlistId, Long stockId);
    void deleteByWatchlistIdAndStockId(Long watchlistId, Long stockId);
}
