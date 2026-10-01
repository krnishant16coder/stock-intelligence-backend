package com.stockintelligence.alert;

import com.stockintelligence.analysis.AIAnalysisProvider;
import com.stockintelligence.analysis.RuleMetrics;
import com.stockintelligence.common.AppProperties;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripwireTest {

    @Mock
    AlertRepository alertRepository;
    @Mock
    AlertRuleRepository ruleRepository;
    @Mock
    AIAnalysisProvider aiProvider;

    RiskAlertService service;
    Stock stock;

    @BeforeEach
    void setUp() throws Exception {
        service = new RiskAlertService(alertRepository, aiProvider, new AppProperties(), ruleRepository);
        stock = new Stock("TCS", "Tata Consultancy Services", "NSE");
        Field id = Stock.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(stock, 3L);
        lenient().when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(alertRepository.existsByDedupKey(any())).thenReturn(false);
        lenient().when(ruleRepository.save(any(AlertRule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AlertRule rule(long id, double threshold, PriceDirection direction,
                                  Severity severity, boolean active, Instant lastTriggered) throws Exception {
        AlertRule r = new AlertRule();
        Field f = AlertRule.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(r, id);
        r.setThresholdPct(threshold);
        r.setDirection(direction);
        r.setSeverity(severity);
        r.setCooldownHours(24);
        r.setActive(active);
        r.setLastTriggeredAt(lastTriggered);
        return r;
    }

    private static RuleMetrics move(double dailyPct) {
        return new RuleMetrics(BigDecimal.valueOf(100), dailyPct, 0.0, 0.0, 1.0,
                false, false, false, false, 30, false);
    }

    @Test
    void upBreachTriggersHighTripwire() throws Exception {
        AlertRule r = rule(1L, 3.0, PriceDirection.BOTH, Severity.HIGH, true, null);
        when(ruleRepository.findByStockIdAndActiveTrue(3L)).thenReturn(List.of(r));

        List<Alert> created = service.evaluateTripwires(stock, move(3.5));

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getAlertType()).isEqualTo(AlertType.PRICE_TRIPWIRE);
        assertThat(created.get(0).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(r.getLastTriggeredAt()).isNotNull();
        verify(ruleRepository).save(r);
    }

    @Test
    void downMoveDoesNotTripUpRule() throws Exception {
        AlertRule r = rule(1L, 3.0, PriceDirection.UP, Severity.HIGH, true, null);
        when(ruleRepository.findByStockIdAndActiveTrue(3L)).thenReturn(List.of(r));

        assertThat(service.evaluateTripwires(stock, move(-4.0))).isEmpty();
    }

    @Test
    void bothTripsEitherDirection() throws Exception {
        AlertRule r = rule(1L, 3.0, PriceDirection.BOTH, Severity.HIGH, true, null);
        when(ruleRepository.findByStockIdAndActiveTrue(3L)).thenReturn(List.of(r));

        assertThat(service.evaluateTripwires(stock, move(-3.2))).hasSize(1);
    }

    @Test
    void cooldownSuppressesRepeat() throws Exception {
        AlertRule r = rule(1L, 3.0, PriceDirection.BOTH, Severity.HIGH, true,
                Instant.now().minusSeconds(3600));
        when(ruleRepository.findByStockIdAndActiveTrue(3L)).thenReturn(List.of(r));

        assertThat(service.evaluateTripwires(stock, move(5.0))).isEmpty();
    }

    @Test
    void inactiveRuleIgnored() throws Exception {
        AlertRule r = rule(1L, 3.0, PriceDirection.BOTH, Severity.HIGH, false, null);
        when(ruleRepository.findByStockIdAndActiveTrue(3L)).thenReturn(List.of(r));

        assertThat(service.evaluateTripwires(stock, move(5.0))).isEmpty();
    }

    @Test
    void nullMetricsSafe() {
        assertThat(service.evaluateTripwires(stock, null)).isEmpty();
    }
}
