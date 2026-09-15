package com.stockintelligence.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;

public record StockAnalysisResponse(Long id, Long stockId, String symbol, String companyName,
                                    String signal, String riskLevel, String priceTrend,
                                    String fundamentalTrend, String newsImpact, String summary,
                                    List<String> keyReasons, Double confidence, boolean criticalAlert) {
    public static StockAnalysisResponse from(StockAnalysis sa, ObjectMapper mapper) {
        List<String> reasons = List.of();
        try {
            if (sa.getKeyReasons() != null && !sa.getKeyReasons().isBlank()) {
                reasons = mapper.readValue(sa.getKeyReasons(), new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
        }
        return new StockAnalysisResponse(sa.getId(), sa.getStock().getId(), sa.getStock().getSymbol(),
                sa.getStock().getCompanyName(), sa.getSignal(), sa.getRiskLevel(), sa.getPriceTrend(),
                sa.getFundamentalTrend(), sa.getNewsImpact(), sa.getSummary(), reasons,
                sa.getConfidence(), sa.isCriticalAlert());
    }
}
