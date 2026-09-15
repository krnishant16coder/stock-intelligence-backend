package com.stockintelligence.stock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockRequest(
        @NotBlank(message = "symbol is required") @Size(max = 32) String symbol,
        @NotBlank(message = "companyName is required") @Size(max = 255) String companyName,
        @NotBlank(message = "exchange is required") @Size(max = 16) String exchange) {}
