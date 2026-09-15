package com.stockintelligence.stock;

import java.time.Instant;

public record StockResponse(Long id, String symbol, String companyName, String exchange,
                            Instant createdAt, Instant updatedAt) {
    public static StockResponse from(Stock s) {
        return new StockResponse(s.getId(), s.getSymbol(), s.getCompanyName(), s.getExchange(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
