package com.stockintelligence.alert;

import com.stockintelligence.common.BadRequestException;
import com.stockintelligence.common.ResourceNotFoundException;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.stock.StockRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    AlertRuleRepository repository;
    @Mock
    StockRepository stocks;

    AlertRuleService service;
    Stock stock;

    @BeforeEach
    void setUp() throws Exception {
        service = new AlertRuleService(repository, stocks);
        stock = new Stock("TCS", "Tata Consultancy Services", "NSE");
        Field id = Stock.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(stock, 3L);
        lenient().when(repository.save(any(AlertRule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAppliesDefaults() {
        when(stocks.findById(3L)).thenReturn(Optional.of(stock));

        AlertRuleResponse out = service.create(
                new AlertRuleRequest(3L, 3.0, null, null, null, null));

        assertThat(out.thresholdPct()).isEqualTo(3.0);
        assertThat(out.direction()).isEqualTo(PriceDirection.BOTH);
        assertThat(out.severity()).isEqualTo(Severity.HIGH);
        assertThat(out.cooldownHours()).isEqualTo(24);
        assertThat(out.active()).isTrue();
    }

    @Test
    void createRejectsBadThreshold() {
        when(stocks.findById(3L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.create(new AlertRuleRequest(3L, 500.0, "BOTH", "HIGH", 24, true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createRejectsBadDirection() {
        when(stocks.findById(3L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.create(new AlertRuleRequest(3L, 3.0, "SIDEWAYS", "HIGH", 24, true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createMissingStock() {
        when(stocks.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new AlertRuleRequest(99L, 3.0, "BOTH", "HIGH", 24, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAppliesPartial() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setStock(stock);
        rule.setThresholdPct(3.0);
        rule.setDirection(PriceDirection.BOTH);
        rule.setSeverity(Severity.HIGH);
        rule.setCooldownHours(24);
        rule.setActive(true);
        when(repository.findById(1L)).thenReturn(Optional.of(rule));

        AlertRuleResponse out = service.update(1L, new AlertRuleUpdate(5.0, null, "MEDIUM", null, false));

        assertThat(out.thresholdPct()).isEqualTo(5.0);
        assertThat(out.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(out.active()).isFalse();
        assertThat(out.direction()).isEqualTo(PriceDirection.BOTH);
        verify(repository).save(rule);
    }

    @Test
    void deleteMissing() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsByStock() {
        when(stocks.findById(3L)).thenReturn(Optional.of(stock));
        AlertRule rule = new AlertRule();
        rule.setStock(stock);
        rule.setThresholdPct(2.0);
        rule.setDirection(PriceDirection.UP);
        rule.setSeverity(Severity.HIGH);
        rule.setCooldownHours(12);
        when(repository.findByStockIdOrderByThresholdPctAsc(3L)).thenReturn(List.of(rule));

        assertThat(service.list(3L)).hasSize(1);
    }
}
