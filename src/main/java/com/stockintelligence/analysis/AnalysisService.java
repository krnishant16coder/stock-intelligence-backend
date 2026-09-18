package com.stockintelligence.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockintelligence.alert.Alert;
import com.stockintelligence.alert.RiskAlertService;
import com.stockintelligence.alert.Severity;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.marketdata.MarketDataService;
import com.stockintelligence.news.NewsArticle;
import com.stockintelligence.news.NewsService;
import com.stockintelligence.notification.NotificationService;
import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.ReportService;
import com.stockintelligence.report.TriggerType;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.watchlist.Watchlist;
import com.stockintelligence.watchlist.WatchlistService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * End-to-end analysis orchestration:
 * load watchlist -> market data -> news -> rule metrics -> AI analysis ->
 * risk evaluation -> report -> alerts -> notifications.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final WatchlistService watchlists;
    private final MarketDataService marketData;
    private final NewsService news;
    private final RuleMetricsService rules;
    private final AIAnalysisProvider aiProvider;
    private final RuleBasedFallbackAnalyzer fallback;
    private final ReportService reportService;
    private final RiskAlertService riskAlerts;
    private final NotificationService notifications;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    /** Last AI call timestamp (pacing watchlist bursts under free TPM caps). */
    private volatile long lastAiCallMs = 0;

    public AnalysisService(WatchlistService watchlists, MarketDataService marketData, NewsService news,
                           RuleMetricsService rules, AIAnalysisProvider aiProvider,
                           RuleBasedFallbackAnalyzer fallback, ReportService reportService,
                           RiskAlertService riskAlerts, NotificationService notifications,
                           AppProperties properties, ObjectMapper objectMapper) {
        this.watchlists = watchlists;
        this.marketData = marketData;
        this.news = news;
        this.rules = rules;
        this.aiProvider = aiProvider;
        this.fallback = fallback;
        this.reportService = reportService;
        this.riskAlerts = riskAlerts;
        this.notifications = notifications;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ReportResponse analyzeWatchlist(Long watchlistId, TriggerType trigger) {
        Watchlist watchlist = watchlists.getOrThrow(watchlistId);
        List<Stock> stocks = watchlists.stocksOf(watchlistId);
        Instant periodStart = Instant.now();
        log.info("Starting {} analysis for watchlist '{}' ({} stocks)", trigger, watchlist.getName(), stocks.size());

        List<ReportService.AnalysisRow> rows = new ArrayList<>();
        List<Alert> allAlerts = new ArrayList<>();

        for (Stock stock : stocks) {
            try {
                PerStockResult r = analyzeOneStock(stock, trigger);
                rows.add(new ReportService.AnalysisRow(stock, r.result(), r.ruleMetricsJson()));
                allAlerts.addAll(r.alerts());
            } catch (Exception e) {
                log.error("Analysis failed for {}: {}", stock.getSymbol(), e.getMessage());
                StockAnalysisResult insufficient = new StockAnalysisResult("INSUFFICIENT_DATA", "MEDIUM",
                        "UNKNOWN", "UNKNOWN", "UNKNOWN",
                        "Analysis failed: " + e.getMessage()
                                + ". This is a system limitation, not financial advice.",
                        List.of("Pipeline error: " + e.getMessage()), 0.1, false).normalized();
                rows.add(new ReportService.AnalysisRow(stock, insufficient, "{}"));
            }
        }

        String summary = buildWatchlistSummary(watchlist.getName(), rows);
        ReportResponse report = reportService.saveReport(watchlist, periodStart, Instant.now(),
                summary, trigger, rows);

        notifyReport(watchlist.getName(), report, rows);
        notifyAlerts(allAlerts);
        log.info("Finished analysis for '{}': report #{}", watchlist.getName(), report.id());
        return report;
    }

    private record PerStockResult(StockAnalysisResult result, String ruleMetricsJson, List<Alert> alerts) {}

    private PerStockResult analyzeOneStock(Stock stock, TriggerType trigger) {
        // 1-2. Market data + news (each failure isolated; analysis continues degraded).
        MarketDataService.MarketDataBundle bundle = null;
        try {
            bundle = marketData.fetchAndStore(stock);
        } catch (ExternalProviderException e) {
            log.warn("Market data failed for {}, continuing with stored history: {}",
                    stock.getSymbol(), e.getMessage());
        }
        List<NewsArticle> articles = List.of();
        try {
            news.fetchAndStore(stock);
        } catch (Exception e) {
            log.warn("News fetch failed for {}: {}", stock.getSymbol(), e.getMessage());
        }
        // Always build AI input from persisted recent news, not just freshly
        // fetched rows. fetchAndStore() returns only NEW articles (deduplicated
        // by URL), so re-running analysis would otherwise see zero news and the
        // AI would return newsImpact=UNKNOWN even though coverage exists.
        articles = news.recentForStock(stock.getId(), properties.getAnalysis().getNewsDays());

        // 3. Rule metrics from fresh provider history, else persisted bars.
        var history = (bundle != null && bundle.history() != null && !bundle.history().isEmpty())
                ? bundle.history()
                : marketData.recentHistory(stock.getId(),
                        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
                                .minusDays(properties.getAnalysis().getHistoryDays()),
                        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        RuleMetrics metrics = rules.calculate(history);
        if (metrics.insufficientData()) {
            log.warn("No price history for {} ({}): fresh bars={}, persisted bars={}. "
                    + "Check market-data provider rate limit / symbol mapping.",
                    stock.getSymbol(), stock.getExchange(),
                    bundle == null || bundle.history() == null ? 0 : bundle.history().size(),
                    history == null ? 0 : history.size());
        }
        if (articles.isEmpty()) {
            log.warn("No recent news for {} in last {} days", stock.getSymbol(),
                    properties.getAnalysis().getNewsDays());
        }
        if (bundle != null && bundle.fundamentals() == null) {
            log.info("Fundamentals unavailable for {} ({}); AI should infer from price/news, not UNKNOWN",
                    stock.getSymbol(), stock.getExchange());
        }
        String metricsJson;
        try {
            metricsJson = objectMapper.writeValueAsString(metrics);
        } catch (Exception e) {
            metricsJson = "{}";
        }

        // 4. AI contextual analysis (validated JSON; fallback on any failure).
        // Cap news sent to the LLM: full newsMaxArticles are still stored for
        // alerts/UI, but only maxNewsForAi go into the prompt (TPM guard).
        List<String> newsSummaries = articles.stream()
                .map(a -> (a.getTitle() == null ? "" : a.getTitle())
                        + (a.getSummary() == null ? "" : " — " + a.getSummary())
                        + (a.getSource() == null ? "" : " [" + a.getSource() + "]"))
                .limit(Math.max(1, properties.getAi().getMaxNewsForAi())).toList();
        StockAnalysisInput input = new StockAnalysisInput(stock.getSymbol(), stock.getCompanyName(),
                stock.getExchange(), bundle == null ? null : bundle.quote(), metrics,
                bundle == null ? null : bundle.fundamentals(), newsSummaries,
                reportService.previousSignal(stock.getId()), trigger.name());
        StockAnalysisResult result;
        try {
            paceAiCalls();
            result = aiProvider.analyze(input).normalized();
        } catch (Exception e) {
            log.warn("AI analysis failed for {}, using rule fallback: {}", stock.getSymbol(), e.getMessage());
            result = fallback.analyze(input, e.getMessage());
        }

        // 5. Risk & alerts (AI context passed through to confirm news candidates).
        List<Alert> alerts = riskAlerts.evaluateAndCreate(stock, metrics, articles, result, null);
        if (Boolean.TRUE.equals(result.criticalAlert())
                && alerts.stream().noneMatch(a -> a.getSeverity() == Severity.CRITICAL)) {
            alerts.addAll(riskAlerts.evaluateAndCreate(stock, metrics, List.of(), result, null));
        }
        return new PerStockResult(result, metricsJson, alerts);
    }

    /**
     * Space out LLM calls so a 10-stock watchlist does not burst through free
     * TPM limits (e.g. Groq gpt-oss-120b: 8k TPM ≈ 4 calls/min at ~2k tokens).
     * Skipped when minIntervalMs {@code <= 0} (tests / paid tiers).
     */
    private synchronized void paceAiCalls() {
        long gap = properties.getAi().getMinIntervalMs();
        if (gap <= 0) {
            return;
        }
        long wait = lastAiCallMs + gap - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastAiCallMs = System.currentTimeMillis();
    }

    private String buildWatchlistSummary(String name, List<ReportService.AnalysisRow> rows) {
        long highRisk = rows.stream().filter(r ->
                "HIGH_RISK".equals(r.result().signal())
                        || "CRITICAL".equals(r.result().riskLevel())
                        || Boolean.TRUE.equals(r.result().criticalAlert())).count();
        long review = rows.stream().filter(r -> "REVIEW".equals(r.result().signal())).count();
        long noData = rows.stream().filter(r -> "INSUFFICIENT_DATA".equals(r.result().signal())).count();
        String top = rows.stream()
                .sorted((a, b) -> riskRank(b.result().riskLevel()) - riskRank(a.result().riskLevel()))
                .limit(3)
                .map(r -> "%s (%s/%s)".formatted(r.stock().getSymbol(), r.result().signal(), r.result().riskLevel()))
                .collect(Collectors.joining("; "));
        return "Watchlist '%s': %d analyzed, %d high-risk/critical, %d need review, %d lack data. Highest risk: %s.".formatted(
                name, rows.size(), highRisk, review, noData, top.isEmpty() ? "n/a" : top);
    }

    private static int riskRank(String risk) {
        return switch (risk == null ? "" : risk) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private void notifyReport(String watchlistName, ReportResponse report, List<ReportService.AnalysisRow> rows) {
        boolean notable = rows.stream().anyMatch(r ->
                properties.getNotifications().getReportSeverities().contains(r.result().riskLevel())
                        || Boolean.TRUE.equals(r.result().criticalAlert()));
        if (notable) {
            notifications.sendReportEmail(watchlistName, report.id(), report.summary());
        } else {
            log.info("Report #{} has no notable risks; summary email skipped", report.id());
        }
    }

    private void notifyAlerts(List<Alert> alerts) {
        for (Alert alert : alerts) {
            if (alert.getSeverity() == Severity.HIGH || alert.getSeverity() == Severity.CRITICAL) {
                notifications.sendAlertEmail(alert);
            }
        }
    }
}
