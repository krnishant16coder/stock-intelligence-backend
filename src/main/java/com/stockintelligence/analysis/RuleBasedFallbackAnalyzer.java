package com.stockintelligence.analysis;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic fallback used when the LLM is unconfigured or fails.
 * Produces an honest low-confidence result from rule metrics only.
 */
@Component
public class RuleBasedFallbackAnalyzer {

    public StockAnalysisResult analyze(StockAnalysisInput input, String reason) {
        RuleMetrics m = input.ruleMetrics();
        if (m == null || m.insufficientData()) {
            return new StockAnalysisResult("INSUFFICIENT_DATA", "MEDIUM", "UNKNOWN", "UNKNOWN",
                    "UNKNOWN", "AI analysis unavailable (" + reason + ") and price history is "
                            + "insufficient for a rule-based read. No action can be justified from data.",
                    List.of("Market data unavailable or incomplete", "AI provider error: " + reason),
                    0.2, false).normalized();
        }
        List<String> reasons = new ArrayList<>();
        String signal = "HOLD";
        String risk = "LOW";
        if (m.monthlyDecline() || m.weeklyDecline()) {
            signal = "REVIEW";
            risk = "HIGH";
            reasons.add("Sustained decline detected (weekly/monthly thresholds breached)");
        } else if (m.sharpDailyMove()) {
            signal = "REVIEW";
            risk = "MEDIUM";
            reasons.add("Sharp single-day move of " + fmt(m.dailyChangePct()) + "%");
        }
        if (m.unusualVolume()) {
            reasons.add("Unusual volume (" + String.format("%.1fx", m.volumeRatio()) + " of average)");
            if ("HOLD".equals(signal)) {
                signal = "REVIEW";
                risk = "MEDIUM";
            }
        }
        if (reasons.isEmpty()) {
            reasons.add("No rule thresholds breached; trend appears stable within measured windows");
        }
        reasons.add("AI contextual analysis unavailable: " + reason);
        return new StockAnalysisResult(signal, risk,
                m.sharpDailyMove() || m.weeklyDecline() ? "VOLATILE/DECLINING" : "STABLE",
                "UNKNOWN",
                (input.newsSummaries() == null || input.newsSummaries().isEmpty()) ? "NO_NEWS" : "UNASSESSED",
                "Rule-based fallback (no LLM): " + String.join("; ", reasons)
                        + ". This is a data observation, not financial advice.",
                reasons, 0.35, "HIGH".equals(risk)).normalized();
    }

    private static String fmt(Double v) {
        return v == null ? "n/a" : String.format("%.2f", v);
    }
}
