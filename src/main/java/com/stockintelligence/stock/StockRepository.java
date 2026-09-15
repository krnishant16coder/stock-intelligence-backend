package com.stockintelligence.stock;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbolAndExchange(String symbol, String exchange);
    List<Stock> findAllByOrderByCompanyNameAsc();
    boolean existsBySymbolAndExchange(String symbol, String exchange);
}
