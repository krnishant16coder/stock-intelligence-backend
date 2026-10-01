package com.stockintelligence.alert;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AlertRuleRequest(
        @NotNull(message = "stockId is required") Long stockId,
        @NotNull(message = "thresholdPct is required")
        @DecimalMin(value = "0.1", message = "thresholdPct must be >= 0.1")
        @DecimalMax(value = "100", message = "thresholdPct must be <= 100") Double thresholdPct,
        String direction,
        String severity,
        Integer cooldownHours,
        Boolean active) {}
