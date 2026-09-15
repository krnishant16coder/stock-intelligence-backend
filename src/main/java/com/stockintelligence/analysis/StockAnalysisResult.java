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
        String sig = signal == null ? "INSUFFICIENT_DATA" : signal.toUpperCase().trim();
        if (!SIGNALS.contains(sig)) {
            throw new IllegalArgumentException("Invalid AI signal: " + signal);
        }
        String risk = riskLevel == null ? "MEDIUM" : riskLevel.toUpperCase().trim();
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
}
