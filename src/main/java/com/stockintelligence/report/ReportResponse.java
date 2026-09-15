package com.stockintelligence.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;

public record ReportResponse(Long id, Long watchlistId, String watchlistName, Instant periodStart,
                             Instant periodEnd, Instant generatedAt, String summary,
                             int stocksAnalyzed, TriggerType triggerType,
                             List<StockAnalysisResponse> analyses) {

    public static ReportResponse detailed(AnalysisReport r, List<StockAnalysis> analyses, ObjectMapper mapper) {
        return new ReportResponse(r.getId(), r.getWatchlist().getId(), r.getWatchlist().getName(),
                r.getPeriodStart(), r.getPeriodEnd(), r.getGeneratedAt(), r.getSummary(),
                r.getStocksAnalyzed(), r.getTriggerType(),
                analyses.stream().map(sa -> StockAnalysisResponse.from(sa, mapper)).toList());
    }

    public static ReportResponse summary(AnalysisReport r) {
        return new ReportResponse(r.getId(), r.getWatchlist().getId(), r.getWatchlist().getName(),
                r.getPeriodStart(), r.getPeriodEnd(), r.getGeneratedAt(), r.getSummary(),
                r.getStocksAnalyzed(), r.getTriggerType(), List.of());
    }
}
