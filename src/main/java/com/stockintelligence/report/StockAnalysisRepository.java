package com.stockintelligence.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockAnalysisRepository extends JpaRepository<StockAnalysis, Long> {
    List<StockAnalysis> findByReportId(Long reportId);

    @Query("select sa from StockAnalysis sa where sa.stock.id = :stockId order by sa.report.generatedAt desc")
    List<StockAnalysis> findLatestForStock(Long stockId, org.springframework.data.domain.Pageable pageable);

    default Optional<StockAnalysis> findLatestForStock(Long stockId) {
        List<StockAnalysis> list = findLatestForStock(stockId,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
