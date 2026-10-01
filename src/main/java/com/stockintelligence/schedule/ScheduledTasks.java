package com.stockintelligence.schedule;

import com.stockintelligence.alert.Alert;
import com.stockintelligence.alert.AlertRepository;
import com.stockintelligence.alert.Severity;
import com.stockintelligence.analysis.AnalysisService;
import com.stockintelligence.analysis.RuleMetrics;
import com.stockintelligence.analysis.RuleMetricsService;
import com.stockintelligence.alert.RiskAlertService;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.marketdata.MarketDataService;
import com.stockintelligence.news.NewsArticle;
import com.stockintelligence.news.NewsService;
import com.stockintelligence.notification.NotificationService;
import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.TriggerType;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.watchlist.Watchlist;
import com.stockintelligence.watchlist.WatchlistRepository;
import com.stockintelligence.watchlist.WatchlistService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs. V1 runs on a single instance:
 * <ul>
 *   <li>Hourly check for due report schedules (DAILY/WEEKLY/MONTHLY).</li>
 *   <li>Independent periodic alert monitoring using the latest available API data.</li>
 * </ul>
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final ScheduleService schedules;
    private final AnalysisService analysis;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistService watchlists;
    private final MarketDataService marketData;
    private final NewsService news;
    private final RuleMetricsService rules;
    private final RiskAlertService riskAlerts;
    private final NotificationService notifications;
    private final AlertRepository alerts;
    private final AppProperties properties;

    public ScheduledTasks(ScheduleService schedules, AnalysisService analysis,
                          WatchlistRepository watchlistRepository, WatchlistService watchlists,
                          MarketDataService marketData, NewsService news, RuleMetricsService rules,
                          RiskAlertService riskAlerts, NotificationService notifications,
                          AlertRepository alerts, AppProperties properties) {
        this.schedules = schedules;
        this.analysis = analysis;
        this.watchlistRepository = watchlistRepository;
        this.watchlists = watchlists;
        this.marketData = marketData;
        this.news = news;
        this.rules = rules;
        this.riskAlerts = riskAlerts;
        this.notifications = notifications;
        this.alerts = alerts;
        this.properties = properties;
    }

    /** Every hour: run due report schedules and advance next_run_at. */
    @Scheduled(cron = "${app.scheduling.report-check-cron:0 0 * * * *}")
    public void runDueReportSchedules() {
        if (!properties.getScheduling().isEnabled()) {
            return;
        }
        List<AnalysisSchedule> due = schedules.dueSchedules(Instant.now());
        log.info("Schedule check: {} due", due.size());
        for (AnalysisSchedule schedule : due) {
            try {
                analysis.analyzeWatchlist(schedule.getWatchlist().getId(), TriggerType.SCHEDULED);
            } catch (Exception e) {
                log.error("Scheduled analysis failed for watchlist {}: {}",
                        schedule.getWatchlist().getId(), e.getMessage());
            } finally {
                schedules.markExecuted(schedule, Instant.now());
            }
        }
    }

    /**
     * Independent critical-alert monitoring. Runs on its own cadence regardless of
     * report frequency and uses the latest available provider data (not tick-level).
     * <p>
     * Each stock is checked once per run even if it sits in several watchlists,
     * and all HIGH/CRITICAL alerts go out as ONE combined mail — never one mail
     * per alert — so a busy news day can't flood the inbox.
     */
    @Scheduled(cron = "${app.scheduling.alert-monitor-cron:0 0 */4 * * *}")
    public void monitorForCriticalAlerts() {
        if (!properties.getScheduling().isEnabled()) {
            return;
        }
        List<Watchlist> active = watchlistRepository.findAllByActiveTrue();
        log.info("Alert monitor: checking {} active watchlists", active.size());
        java.util.Map<Long, Stock> uniqueStocks = new java.util.LinkedHashMap<>();
        for (Watchlist watchlist : active) {
            for (Stock stock : watchlists.stocksOf(watchlist.getId())) {
                uniqueStocks.putIfAbsent(stock.getId(), stock);
            }
        }
        List<Alert> urgent = new ArrayList<>();
        for (Stock stock : uniqueStocks.values()) {
            try {
                for (Alert alert : checkStock(stock)) {
                    if (alert.getSeverity() == Severity.HIGH || alert.getSeverity() == Severity.CRITICAL) {
                        urgent.add(alert);
                    }
                }
            } catch (Exception e) {
                log.warn("Alert monitor failed for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
        if (urgent.isEmpty()) {
            log.info("Alert monitor: nothing urgent across {} stocks", uniqueStocks.size());
            return;
        }
        notifications.sendUrgentDigestEmail(today().toString(), urgent);
        log.info("Alert monitor: mailed {} urgent signals across {} stocks",
                urgent.size(), uniqueStocks.size());
    }

    /**
     * Daily market-close digest: analyzes every active watchlist after NSE close
     * and mails one combined report covering the whole portfolio — even when
     * nothing is HIGH/CRITICAL (unlike severity-gated report mails).
     * Zone is pinned to Asia/Kolkata so Azure's UTC default can't shift it.
     */
    @Scheduled(cron = "${app.scheduling.eod-digest-cron:0 0 16 * * MON-FRI}", zone = "Asia/Kolkata")
    public void sendEodDigest() {
        if (!properties.getScheduling().isEnabled()) {
            return;
        }
        List<Watchlist> active = watchlistRepository.findAllByActiveTrue();
        if (active.isEmpty()) {
            log.info("EOD digest: no active watchlists");
            return;
        }
        List<ReportResponse> reports = new ArrayList<>();
        int stocks = 0;
        for (Watchlist watchlist : active) {
            try {
                ReportResponse report = analysis.analyzeWatchlist(watchlist.getId(), TriggerType.EOD);
                reports.add(report);
                stocks += report.stocksAnalyzed();
            } catch (Exception e) {
                log.error("EOD digest: analysis failed for watchlist {}: {}",
                        watchlist.getId(), e.getMessage());
            }
        }
        if (reports.isEmpty()) {
            log.warn("EOD digest: all analyses failed, no email sent");
            return;
        }
        String dateLabel = today().toString();
        notifications.sendEodDigestEmail(dateLabel, reports);
        log.info("EOD digest: mailed {} watchlists, {} stocks for {}", reports.size(), stocks, dateLabel);
    }

    /**
     * Midday roundup: one combined mail for today's MEDIUM signals (HIGH/CRITICAL
     * already went out instantly). Stays silent when there is nothing to report.
     * Zone is pinned to Asia/Kolkata so Azure's UTC default can't shift it.
     */
    @Scheduled(cron = "${app.scheduling.medium-roundup-cron:0 0 13 * * MON-FRI}", zone = "Asia/Kolkata")
    public void sendMediumRoundup() {
        if (!properties.getScheduling().isEnabled()) {
            return;
        }
        Instant since = today().atStartOfDay(ZONE).toInstant();
        List<Alert> mediums = alerts.findBySeverityAndCreatedAtAfterOrderByCreatedAtDesc(
                Severity.MEDIUM, since);
        if (mediums.isEmpty()) {
            log.info("Medium roundup: nothing to report for {}", today());
            return;
        }
        notifications.sendMediumRoundupEmail(today().toString(), mediums);
        log.info("Medium roundup: mailed {} medium signals for {}", mediums.size(), today());
    }

    private List<Alert> checkStock(Stock stock) {
        MarketDataService.MarketDataBundle bundle;
        try {
            bundle = marketData.fetchAndStore(stock);
        } catch (Exception e) {
            log.warn("Monitor: market data failed for {}: {}", stock.getSymbol(), e.getMessage());
            return List.of();
        }
        List<NewsArticle> articles;
        try {
            articles = news.fetchAndStore(stock);
        } catch (Exception e) {
            log.warn("Monitor: news failed for {}: {}", stock.getSymbol(), e.getMessage());
            articles = news.recentForStock(stock.getId(), properties.getAnalysis().getNewsDays());
        }
        RuleMetrics metrics = rules.calculate(bundle.history());
        // No AI context here by default; RiskAlertService makes one best-effort
        // AI call per news candidate only. Pass null input to keep the monitor cheap
        // when there is no suspicious news (detect() gates AI usage).
        var history = bundle.history();
        List<Alert> created = riskAlerts.evaluateAndCreate(stock, metrics, articles, null, null);
        log.debug("Monitor: {} alerts for {} ({} bars)", created.size(), stock.getSymbol(), history.size());
        return created;
    }

    /** Shared helper for tests. */
    LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
