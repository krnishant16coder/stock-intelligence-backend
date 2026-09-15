package com.stockintelligence.marketdata;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketDataRepository extends JpaRepository<MarketDataPoint, Long> {
    List<MarketDataPoint> findByStockIdAndTradingDateBetweenOrderByTradingDateAsc(
            Long stockId, LocalDate from, LocalDate to);
    Optional<MarketDataPoint> findTopByStockIdOrderByTradingDateDesc(Long stockId);
    boolean existsByStockIdAndTradingDateAndDataSource(Long stockId, LocalDate tradingDate, String dataSource);
}
