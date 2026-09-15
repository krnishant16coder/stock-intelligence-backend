package com.stockintelligence.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.analysis.StockAnalysisResult;
import com.stockintelligence.common.ResourceNotFoundException;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.watchlist.Watchlist;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final AnalysisReportRepository reports;
    private final StockAnalysisRepository analyses;
    private final ObjectMapper objectMapper;

    public ReportService(AnalysisReportRepository reports, StockAnalysisRepository analyses,
                         ObjectMapper objectMapper) {
        this.reports = reports;
        this.analyses = analyses;
        this.objectMapper = objectMapper;
    }

    public record AnalysisRow(Stock stock, StockAnalysisResult result, String ruleMetricsJson) {}

    @Transactional
    public ReportResponse saveReport(Watchlist watchlist, Instant periodStart, Instant periodEnd,
                                     String summary, TriggerType triggerType, List<AnalysisRow> rows) {
        AnalysisReport report = new AnalysisReport();
        report.setWatchlist(watchlist);
        report.setPeriodStart(periodStart);
        report.setPeriodEnd(periodEnd);
        report.setSummary(summary);
        report.setStocksAnalyzed(rows.size());
        report.setTriggerType(triggerType);
        AnalysisReport saved = reports.save(report);
        for (AnalysisRow row : rows) {
            StockAnalysis sa = new StockAnalysis();
            sa.setReport(saved);
            sa.setStock(row.stock());
            sa.setSignal(row.result().signal());
            sa.setRiskLevel(row.result().riskLevel());
            sa.setPriceTrend(row.result().priceTrend());
            sa.setFundamentalTrend(row.result().fundamentalTrend());
            sa.setNewsImpact(row.result().newsImpact());
            sa.setSummary(row.result().summary());
            sa.setConfidence(row.result().confidence());
            sa.setCriticalAlert(Boolean.TRUE.equals(row.result().criticalAlert()));
            sa.setRuleMetricsJson(row.ruleMetricsJson());
            try {
                sa.setKeyReasons(objectMapper.writeValueAsString(row.result().keyReasons()));
            } catch (Exception e) {
                sa.setKeyReasons("[]");
            }
            analyses.save(sa);
        }
        return getReport(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> list(Long watchlistId) {
        List<AnalysisReport> all = watchlistId == null
                ? reports.findAll().stream()
                        .sorted((a, b) -> b.getGeneratedAt().compareTo(a.getGeneratedAt())).toList()
                : reports.findByWatchlistIdOrderByGeneratedAtDesc(watchlistId);
        return all.stream().map(ReportResponse::summary).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long id) {
        AnalysisReport report = reports.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
        return ReportResponse.detailed(report, analyses.findByReportId(id), objectMapper);
    }

    @Transactional(readOnly = true)
    public List<StockAnalysisResponse> latestForStock(Long stockId, int limit) {
        return analyses.findLatestForStock(stockId, PageRequest.of(0, Math.min(Math.max(limit, 1), 50)))
                .stream().map(sa -> StockAnalysisResponse.from(sa, objectMapper)).toList();
    }

    @Transactional(readOnly = true)
    public String previousSignal(Long stockId) {
        return analyses.findLatestForStock(stockId).map(StockAnalysis::getSignal).orElse(null);
    }
}
