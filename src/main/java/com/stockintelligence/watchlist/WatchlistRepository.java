package com.stockintelligence.watchlist;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findAllByActiveTrue();
}
