package com.stockintelligence.alert;

public record AlertRuleUpdate(Double thresholdPct, String direction, String severity,
                              Integer cooldownHours, Boolean active) {}
