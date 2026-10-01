package com.stockintelligence.alert;

import java.time.Instant;

public record AlertRuleResponse(Long id, Long stockId, String symbol, double thresholdPct,
                                PriceDirection direction, Severity severity, int cooldownHours,
                                boolean active, Instant lastTriggeredAt, Instant createdAt) {
    public static AlertRuleResponse from(AlertRule r) {
        return new AlertRuleResponse(r.getId(), r.getStock().getId(), r.getStock().getSymbol(),
                r.getThresholdPct(), r.getDirection(), r.getSeverity(), r.getCooldownHours(),
                r.isActive(), r.getLastTriggeredAt(), r.getCreatedAt());
    }
}
