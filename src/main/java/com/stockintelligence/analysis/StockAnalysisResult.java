package com.stockintelligence.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Structured AI output. Validated before persistence. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockAnalysisResult(String signal, String riskLevel, String priceTrend,
                                  String fundamentalTrend, String newsImpact, String summary,
                                  List<String> keyReasons, Double confidence,
                                  Boolean criticalAlert) {

    private static final List<String> SIGNALS = List.of("BUY_MORE", "HOLD", "REVIEW", "HIGH_RISK", "INSUFFICIENT_DATA");
    private static final List<String> RISKS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public StockAnalysisResult normalized() {
        String sig = aliasSignal(signal);
        if (!SIGNALS.contains(sig)) {
            throw new IllegalArgumentException("Invalid AI signal: " + signal);
        }
        String risk = aliasRisk(riskLevel);
        if (!RISKS.contains(risk)) {
            throw new IllegalArgumentException("Invalid AI riskLevel: " + riskLevel);
        }
        List<String> reasons = keyReasons == null ? List.of() : keyReasons.stream()
                .filter(r -> r != null && !r.isBlank()).toList();
        double conf = confidence == null ? 0.5 : Math.min(1.0, Math.max(0.0, confidence));
        return new StockAnalysisResult(sig, risk,
                orDefault(priceTrend, "UNKNOWN"), orDefault(fundamentalTrend, "UNKNOWN"),
                orDefault(newsImpact, "NEUTRAL"), summary == null ? "" : summary,
                reasons, conf, Boolean.TRUE.equals(criticalAlert));
    }

    private static String orDefault(String v, String d) {
        return (v == null || v.isBlank()) ? d : v.trim();
    }

    /** Map common model near-misses to the strict signal enum. */
    static String aliasSignal(String signal) {
        if (signal == null || signal.isBlank()) {
            return "INSUFFICIENT_DATA";
        }
        return switch (signal.strip().toUpperCase().replace('-', '_').replace(' ', '_')) {
            case "BUY" -> "BUY_MORE";
            case "SELL", "HIGH_RISK" -> "HIGH_RISK";
            case "WATCH", "WATCHLIST" -> "REVIEW";
            default -> signal.strip().toUpperCase().replace('-', '_').replace(' ', '_');
        };
    }

    /** Map common model near-misses to the strict risk enum. */
    static String aliasRisk(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return "MEDIUM";
        }
        return switch (riskLevel.strip().toUpperCase()) {
            case "MODERATE", "MED" -> "MEDIUM";
            case "CRIT", "SEVERE" -> "CRITICAL";
            default -> riskLevel.strip().toUpperCase();
        };
    }
}
