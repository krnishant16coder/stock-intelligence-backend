package com.stockintelligence.schedule;

import com.stockintelligence.alert.RiskAlertService;
import com.stockintelligence.analysis.AnalysisService;
import com.stockintelligence.analysis.RuleMetricsService;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.marketdata.MarketDataService;
import com.stockintelligence.news.NewsService;
import com.stockintelligence.notification.NotificationService;
import com.stockintelligence.report.ReportResponse;
import com.stockintelligence.report.StockAnalysisResponse;
import com.stockintelligence.report.TriggerType;
import com.stockintelligence.watchlist.Watchlist;
import com.stockintelligence.watchlist.WatchlistRepository;
import com.stockintelligence.watchlist.WatchlistService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EodDigestTest {

    @Mock
    ScheduleService schedules;
    @Mock
    AnalysisService analysis;
    @Mock
    WatchlistRepository watchlistRepository;
    @Mock
    WatchlistService watchlists;
    @Mock
    MarketDataService marketData;
    @Mock
    NewsService news;
    @Mock
    RuleMetricsService rules;
    @Mock
    RiskAlertService riskAlerts;
    @Mock
    NotificationService notifications;

    AppProperties properties;
    ScheduledTasks tasks;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        tasks = new ScheduledTasks(schedules, analysis, watchlistRepository, watchlists,
                marketData, news, rules, riskAlerts, notifications, properties);
    }

    private static Watchlist watchlist(long id, String name) throws Exception {
        Watchlist w = new Watchlist(name);
        Field f = Watchlist.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(w, id);
        return w;
    }

    private static ReportResponse mediumReport(long reportId, long watchlistId, String name) {
        StockAnalysisResponse a = new StockAnalysisResponse(1L, 3L, "TCS",
                "Tata Consultancy Services", "HOLD", "MEDIUM", "VOLATILE",
                "WEAKENING", "NEGATIVE", "Mixed signals, review warranted.",
                List.of("Monthly down 12%"), 0.6, false);
        Instant now = Instant.now();
        return new ReportResponse(reportId, watchlistId, name, now, now, now,
                "Watchlist '" + name + "': 1 analyzed, 0 high-risk/critical.",
                1, TriggerType.EOD, List.of(a));
    }

    @Test
    void sendsCombinedDigestEvenWhenAllMedium() throws Exception {
        when(watchlistRepository.findAllByActiveTrue())
                .thenReturn(List.of(watchlist(1L, "TCS")));
        when(analysis.analyzeWatchlist(1L, TriggerType.EOD))
                .thenReturn(mediumReport(14L, 1L, "TCS"));

        tasks.sendEodDigest();

        verify(analysis).analyzeWatchlist(1L, TriggerType.EOD);
        verify(notifications).sendEodDigestEmail(anyString(), eq(1), eq(1), contains("TCS"));
    }

    @Test
    void skipsWhenSchedulingDisabled() {
        properties.getScheduling().setEnabled(false);

        tasks.sendEodDigest();

        verify(watchlistRepository, never()).findAllByActiveTrue();
        verify(notifications, never()).sendEodDigestEmail(anyString(), anyInt(), anyInt(), anyString());
    }

    @Test
    void skipsWhenNoActiveWatchlists() {
        when(watchlistRepository.findAllByActiveTrue()).thenReturn(List.of());

        tasks.sendEodDigest();

        verify(analysis, never()).analyzeWatchlist(1L, TriggerType.EOD);
        verify(notifications, never()).sendEodDigestEmail(anyString(), anyInt(), anyInt(), anyString());
    }

    @Test
    void continuesWhenOneWatchlistFails() throws Exception {
        when(watchlistRepository.findAllByActiveTrue())
                .thenReturn(List.of(watchlist(1L, "TCS"), watchlist(2L, "hdfc bank")));
        when(analysis.analyzeWatchlist(eq(1L), eq(TriggerType.EOD)))
                .thenThrow(new RuntimeException("provider down"));
        when(analysis.analyzeWatchlist(eq(2L), eq(TriggerType.EOD)))
                .thenReturn(mediumReport(15L, 2L, "hdfc bank"));

        tasks.sendEodDigest();

        verify(notifications).sendEodDigestEmail(anyString(), eq(1), eq(1), contains("hdfc bank"));
    }
}
