package com.stockintelligence.analysis;

import com.stockintelligence.common.AppProperties;
import com.stockintelligence.marketdata.HistoricalPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Pure deterministic calculations over price/volume history.
 * No AI, no I/O — fully unit-testable.
 */
@Service
public class RuleMetricsService {

    private final AppProperties properties;

    public RuleMetricsService(AppProperties properties) {
        this.properties = properties;
    }

    public RuleMetrics calculate(List<HistoricalPrice> history) {
        if (history == null || history.size() < 2) {
            return new RuleMetrics(null, null, null, null, null, false, false, false, false,
                    history == null ? 0 : history.size(), true);
        }
        List<HistoricalPrice> sorted = history.stream()
                .sorted((a, b) -> a.tradingDate().compareTo(b.tradingDate())).toList();
        HistoricalPrice latest = sorted.get(sorted.size() - 1);
        HistoricalPrice prev = sorted.get(sorted.size() - 2);

        Double daily = pctChange(prev.close(), latest.close());
        Double weekly = pctChange(closeBack(sorted, 5), latest.close());
        Double monthly = pctChange(closeBack(sorted, 21), latest.close());
        Double volumeRatio = volumeRatio(sorted);

        var thresholds = properties.getAnalysis();
        boolean sharpDaily = daily != null && Math.abs(daily) >= thresholds.getDailyMovePct();
        boolean weeklyDecline = weekly != null && weekly <= -thresholds.getWeeklyDeclinePct();
        boolean monthlyDecline = monthly != null && monthly <= -thresholds.getMonthlyDeclinePct();
        boolean unusualVolume = volumeRatio != null && volumeRatio >= thresholds.getVolumeSurgeMultiple();

        return new RuleMetrics(latest.close(), daily, weekly, monthly, volumeRatio,
                unusualVolume, sharpDaily, weeklyDecline, monthlyDecline, sorted.size(), false);
    }

    private static BigDecimal closeBack(List<HistoricalPrice> sorted, int sessionsBack) {
        int idx = sorted.size() - 1 - sessionsBack;
        if (idx < 0) {
            return null;
        }
        return sorted.get(idx).close();
    }

    static Double pctChange(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return to.subtract(from).divide(from, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private Double volumeRatio(List<HistoricalPrice> sorted) {
        int window = properties.getAnalysis().getVolumeAverageDays();
        if (sorted.size() < window + 1) {
            return null;
        }
        HistoricalPrice latest = sorted.get(sorted.size() - 1);
        if (latest.volume() == null || latest.volume() == 0) {
            return null;
        }
        List<HistoricalPrice> prior = sorted.subList(sorted.size() - 1 - window, sorted.size() - 1);
        double avg = prior.stream().filter(h -> h.volume() != null).mapToLong(HistoricalPrice::volume)
                .average().orElse(0);
        if (avg <= 0) {
            return null;
        }
        return latest.volume() / avg;
    }
}
