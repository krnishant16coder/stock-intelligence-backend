package com.stockintelligence.alert;

import java.time.Instant;

public record AlertResponse(Long id, Long stockId, String symbol, String companyName, AlertType alertType,
                            Severity severity, String message, Instant createdAt, AlertStatus status) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(a.getId(), a.getStock().getId(), a.getStock().getSymbol(),
                a.getStock().getCompanyName(), a.getAlertType(), a.getSeverity(),
                a.getMessage(), a.getCreatedAt(), a.getStatus());
    }
}
