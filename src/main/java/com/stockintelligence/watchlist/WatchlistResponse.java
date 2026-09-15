package com.stockintelligence.watchlist;

import com.stockintelligence.stock.StockResponse;
import java.time.Instant;
import java.util.List;

public record WatchlistResponse(Long id, String name, boolean active, Instant createdAt,
                                List<StockResponse> stocks) {}
