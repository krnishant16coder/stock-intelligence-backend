package com.stockintelligence.report;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    List<AnalysisReport> findByWatchlistIdOrderByGeneratedAtDesc(Long watchlistId);
}
