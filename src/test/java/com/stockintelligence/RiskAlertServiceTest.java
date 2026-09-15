package com.stockintelligence;

import com.stockintelligence.alert.Alert;
import com.stockintelligence.alert.AlertRepository;
import com.stockintelligence.alert.AlertType;
import com.stockintelligence.alert.RiskAlertService;
import com.stockintelligence.alert.Severity;
import com.stockintelligence.analysis.AIAnalysisProvider;
import com.stockintelligence.analysis.RuleMetrics;
import com.stockintelligence.analysis.StockAnalysisInput;
import com.stockintelligence.analysis.StockAnalysisResult;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.news.NewsArticle;
import com.stockintelligence.stock.Stock;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskAlertServiceTest {

    @Mock
    AlertRepository alertRepository;
    @Mock
    AIAnalysisProvider aiProvider;

    RiskAlertService service;
    Stock stock;

    @BeforeEach
    void setUp() throws Exception {
        service = new RiskAlertService(alertRepository, aiProvider, new AppProperties());
        stock = new Stock("RELIANCE", "Reliance Industries", "NSE");
        Field id = Stock.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(stock, 7L);
        lenient().when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RuleMetrics crashMetrics() {
        return new RuleMetrics(BigDecimal.valueOf(2200), -8.5, -12.0, -4.0, 4.2,
                true, true, true, false, 60, false);
    }

    @Test
    void createsPriceAndVolumeAlertsWithDedup() {
        when(alertRepository.existsByDedupKey(any())).thenReturn(false);
        List<Alert> created = service.evaluateAndCreate(stock, crashMetrics(), List.of(), null, null);
        assertThat(created).extracting(a -> a.getAlertType()).contains(
                AlertType.PRICE_DROP, AlertType.WEEKLY_DECLINE, AlertType.VOLUME_SURGE);
        assertThat(created).extracting(Alert::getDedupKey).doesNotContainNull();
    }

    @Test
    void skipsDuplicates() {
        when(alertRepository.existsByDedupKey(any())).thenReturn(true);
        assertThat(service.evaluateAndCreate(stock, crashMetrics(), List.of(), null, null)).isEmpty();
    }

    @Test
    void newsKeywordTriggersRegulatoryAlert() {
        NewsArticle article = new NewsArticle();
        article.setTitle("SEBI issues show-cause notice to Reliance over disclosure lapse");
        article.setSummary("The regulator has sought a response within 21 days.");
        article.setSource("Example Times");
        article.setUrl("https://example.com/sebi-notice-123");
        article.setPublishedAt(Instant.now());
        when(alertRepository.existsByDedupKey(any())).thenReturn(false);

        List<Alert> created = service.evaluateAndCreate(stock, null, List.of(article), null, null);
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getAlertType()).isEqualTo(AlertType.REGULATORY_RISK);
        assertThat(created.get(0).getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void aiContextEscalatesToCritical() {
        NewsArticle article = new NewsArticle();
        article.setTitle("Auditor resigns citing governance concerns at Reliance unit");
        article.setSummary("Forensic audit demanded by investors.");
        article.setUrl("https://example.com/audit-9");
        article.setPublishedAt(Instant.now());
        when(alertRepository.existsByDedupKey(any())).thenReturn(false);
        StockAnalysisResult ctx = new StockAnalysisResult("HIGH_RISK", "CRITICAL", "DOWN",
                "DETERIORATING", "VERY_NEGATIVE", "Serious", List.of("Auditor exit"), 0.9, true);

        List<Alert> created = service.evaluateAndCreate(stock, null, List.of(article), ctx, null);
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void noAlertsWhenCalmAndNoNews() {
        RuleMetrics calm = new RuleMetrics(BigDecimal.valueOf(2500), 0.4, 1.2, 3.0, 0.9,
                false, false, false, false, 60, false);
        assertThat(service.evaluateAndCreate(stock, calm, List.of(), null, null)).isEmpty();
    }

    @Test
    void aiInputUsedWhenNoContextAvailable() {
        NewsArticle article = new NewsArticle();
        article.setTitle("Company reports net loss widening, misses estimates");
        article.setSummary("Q3 net loss widens on weak margins.");
        article.setUrl("https://example.com/earn-1");
        article.setPublishedAt(Instant.now());
        when(alertRepository.existsByDedupKey(any())).thenReturn(false);
        StockAnalysisInput input = new StockAnalysisInput("RELIANCE", "Reliance", "NSE",
                null, null, null, List.of("net loss"), null, "MANUAL");
        when(aiProvider.analyze(input)).thenReturn(new StockAnalysisResult("REVIEW", "LOW", "DOWN",
                "WEAK", "NEGATIVE", "ok", List.of("loss"), 0.6, false));

        List<Alert> created = service.evaluateAndCreate(stock, null, List.of(article), null, input);
        // AI judged LOW risk -> immaterial candidate dropped.
        assertThat(created).isEmpty();
    }
}
