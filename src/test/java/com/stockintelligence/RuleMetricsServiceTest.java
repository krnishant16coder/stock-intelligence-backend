package com.stockintelligence;

import com.stockintelligence.analysis.RuleMetrics;
import com.stockintelligence.analysis.RuleMetricsService;
import com.stockintelligence.common.AppProperties;
import com.stockintelligence.marketdata.HistoricalPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMetricsServiceTest {

    private final RuleMetricsService service = new RuleMetricsService(new AppProperties());

    private static List<HistoricalPrice> risingHistory(int days, double startPrice) {
        List<HistoricalPrice> out = new ArrayList<>();
        LocalDate today = LocalDate.now().minusDays(1);
        for (int i = days - 1; i >= 0; i--) {
            double price = startPrice * (1 + 0.002 * (days - i));
            out.add(new HistoricalPrice(today.minusDays(i), bd(price), bd(price), bd(price), bd(price), 1_000_000L));
        }
        return out;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void insufficientDataWhenTooFewBars() {
        RuleMetrics m = service.calculate(List.of());
        assertThat(m.insufficientData()).isTrue();
        assertThat(m.dailyChangePct()).isNull();
    }

    @Test
    void computesDailyWeeklyMonthlyChanges() {
        List<HistoricalPrice> history = risingHistory(30, 100.0);
        RuleMetrics m = service.calculate(history);
        assertThat(m.insufficientData()).isFalse();
        assertThat(m.dailyChangePct()).isNotNull().isGreaterThan(0);
        assertThat(m.weeklyChangePct()).isGreaterThan(m.dailyChangePct());
        assertThat(m.monthlyChangePct()).isNotNull().isGreaterThan(m.weeklyChangePct());
    }

    @Test
    void detectsSharpDailyMoveAndVolumeSurge() {
        List<HistoricalPrice> history = risingHistory(30, 100.0);
        // Crash the latest bar -8% with 5x volume.
        HistoricalPrice last = history.get(history.size() - 1);
        history.set(history.size() - 1, new HistoricalPrice(last.tradingDate(), bd(95), bd(96),
                bd(90), bd(92), 5_000_000L));
        RuleMetrics m = service.calculate(history);
        assertThat(m.sharpDailyMove()).isTrue();
        assertThat(m.unusualVolume()).isTrue();
        assertThat(m.dailyChangePct()).isLessThan(-5.0);
    }

    @Test
    void detectsWeeklyDecline() {
        List<HistoricalPrice> history = new ArrayList<>();
        LocalDate today = LocalDate.now().minusDays(1);
        // Steady bleed: -2.5% per session for 8 sessions ≈ -18% weekly.
        double price = 200.0;
        for (int i = 29; i >= 0; i--) {
            if (i < 8) {
                price = price * 0.975;
            }
            out(history, today.minusDays(i), price);
        }
        RuleMetrics m = service.calculate(history);
        assertThat(m.weeklyDecline()).isTrue();
    }

    private static void out(List<HistoricalPrice> list, LocalDate date, double price) {
        list.add(new HistoricalPrice(date, bd(price), bd(price), bd(price), bd(price), 500_000L));
    }
}
