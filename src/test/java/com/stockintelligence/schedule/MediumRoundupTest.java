package com.stockintelligence.schedule;

import com.stockintelligence.alert.Alert;
import com.stockintelligence.alert.AlertRepository;
import com.stockintelligence.alert.AlertType;
import com.stockintelligence.alert.RiskAlertService;
import com.stockintelligence.alert.Severity;
import com.stockintelligence.analysis.AnalysisService;
import com.stockintelligence.analysis.RuleMetricsService;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.marketdata.MarketDataService;
import com.stockintelligence.news.NewsService;
import com.stockintelligence.notification.NotificationService;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.watchlist.WatchlistRepository;
import com.stockintelligence.watchlist.WatchlistService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediumRoundupTest {

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
    @Mock
    AlertRepository alertRepository;

    AppProperties properties;
    ScheduledTasks tasks;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        tasks = new ScheduledTasks(schedules, analysis, watchlistRepository, watchlists,
                marketData, news, rules, riskAlerts, notifications, alertRepository, properties);
    }

    private static Alert mediumAlert() throws Exception {
        var ctor = Alert.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Alert alert = ctor.newInstance();
        alert.setStock(new Stock("TCS", "Tata Consultancy Services", "NSE"));
        alert.setAlertType(AlertType.NEGATIVE_EARNINGS);
        alert.setSeverity(Severity.MEDIUM);
        alert.setMessage("NEGATIVE_EARNINGS: earnings miss (Ndtv)");
        return alert;
    }

    @Test
    void sendsTodaysMediums() throws Exception {
        when(alertRepository.findBySeverityAndCreatedAtAfterOrderByCreatedAtDesc(eq(Severity.MEDIUM), any()))
                .thenReturn(List.of(mediumAlert()));

        tasks.sendMediumRoundup();

        verify(notifications).sendMediumRoundupEmail(anyString(), any());
    }

    @Test
    void silentWhenEmpty() {
        when(alertRepository.findBySeverityAndCreatedAtAfterOrderByCreatedAtDesc(eq(Severity.MEDIUM), any()))
                .thenReturn(List.of());

        tasks.sendMediumRoundup();

        verify(notifications, never()).sendMediumRoundupEmail(anyString(), any());
    }

    @Test
    void skipsWhenSchedulingDisabled() {
        properties.getScheduling().setEnabled(false);

        tasks.sendMediumRoundup();

        verify(alertRepository, never()).findBySeverityAndCreatedAtAfterOrderByCreatedAtDesc(any(), any());
        verify(notifications, never()).sendMediumRoundupEmail(anyString(), any());
    }
}
