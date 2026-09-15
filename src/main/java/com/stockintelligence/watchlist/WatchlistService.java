package com.stockintelligence.watchlist;

import com.stockintelligence.common.BadRequestException;
import com.stockintelligence.common.ResourceNotFoundException;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.stock.StockRepository;
import com.stockintelligence.stock.StockResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlists;
    private final WatchlistStockRepository members;
    private final StockRepository stocks;

    public WatchlistService(WatchlistRepository watchlists, WatchlistStockRepository members,
                            StockRepository stocks) {
        this.watchlists = watchlists;
        this.members = members;
        this.stocks = stocks;
    }

    @Transactional
    public WatchlistResponse create(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Watchlist name is required");
        }
        Watchlist saved = watchlists.save(new Watchlist(name.trim()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WatchlistResponse> list() {
        return watchlists.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Watchlist getOrThrow(Long id) {
        return watchlists.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist not found: " + id));
    }

    @Transactional
    public WatchlistResponse addStock(Long watchlistId, Long stockId) {
        Watchlist watchlist = getOrThrow(watchlistId);
        Stock stock = stocks.findById(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + stockId));
        if (members.existsByWatchlistIdAndStockId(watchlistId, stockId)) {
            throw new BadRequestException("Stock already in watchlist");
        }
        members.save(new WatchlistStock(watchlist, stock));
        return toResponse(watchlist);
    }

    @Transactional
    public void removeStock(Long watchlistId, Long stockId) {
        getOrThrow(watchlistId);
        if (!members.existsByWatchlistIdAndStockId(watchlistId, stockId)) {
            throw new ResourceNotFoundException("Stock " + stockId + " not in watchlist " + watchlistId);
        }
        members.deleteByWatchlistIdAndStockId(watchlistId, stockId);
    }

    @Transactional(readOnly = true)
    public List<Stock> stocksOf(Long watchlistId) {
        getOrThrow(watchlistId);
        return members.findByWatchlistId(watchlistId).stream().map(WatchlistStock::getStock).toList();
    }

    private WatchlistResponse toResponse(Watchlist w) {
        List<StockResponse> stockList = members.findByWatchlistId(w.getId()).stream()
                .map(WatchlistStock::getStock).map(StockResponse::from).toList();
        return new WatchlistResponse(w.getId(), w.getName(), w.isActive(), w.getCreatedAt(), stockList);
    }
}
