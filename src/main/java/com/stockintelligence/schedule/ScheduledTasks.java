package com.stockintelligence.schedule;

import com.stockintelligence.alert.Alert;
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
import com.stockintelligence.report.TriggerType;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.watchlist.Watchlist;
import com.stockintelligence.watchlist.WatchlistRepository;
import com.stockintelligence.watchlist.WatchlistService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final AppProperties properties;

    public ScheduledTasks(ScheduleService schedules, AnalysisService analysis,
                          WatchlistRepository watchlistRepository, WatchlistService watchlists,
                          MarketDataService marketData, NewsService news, RuleMetricsService rules,
                          RiskAlertService riskAlerts, NotificationService notifications,
                          AppProperties properties) {
        this.schedules = schedules;
        this.analysis = analysis;
        this.watchlistRepository = watchlistRepository;
        this.watchlists = watchlists;
        this.marketData = marketData;
        this.news = news;
        this.rules = rules;
        this.riskAlerts = riskAlerts;
        this.notifications = notifications;
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
     */
    @Scheduled(cron = "${app.scheduling.alert-monitor-cron:0 0 */4 * * *}")
    public void monitorForCriticalAlerts() {
        if (!properties.getScheduling().isEnabled()) {
            return;
        }
        List<Watchlist> active = watchlistRepository.findAllByActiveTrue();
        log.info("Alert monitor: checking {} active watchlists", active.size());
        for (Watchlist watchlist : active) {
            for (Stock stock : watchlists.stocksOf(watchlist.getId())) {
                try {
                    checkStock(stock);
                } catch (Exception e) {
                    log.warn("Alert monitor failed for {}: {}", stock.getSymbol(), e.getMessage());
                }
            }
        }
    }

    private void checkStock(Stock stock) {
        MarketDataService.MarketDataBundle bundle;
        try {
            bundle = marketData.fetchAndStore(stock);
        } catch (Exception e) {
            log.warn("Monitor: market data failed for {}: {}", stock.getSymbol(), e.getMessage());
            return;
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
        for (Alert alert : created) {
            if (alert.getSeverity() == Severity.HIGH || alert.getSeverity() == Severity.CRITICAL) {
                notifications.sendAlertEmail(alert);
            }
        }
    }

    /** Shared helper for tests. */
    LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
