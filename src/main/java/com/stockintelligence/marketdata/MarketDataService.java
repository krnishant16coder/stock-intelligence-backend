package com.stockintelligence.marketdata;

import com.stockintelligence.common.AppProperties;
import com.stockintelligence.common.ExternalProviderException;
import com.stockintelligence.stock.Stock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches market data through the configured {@link MarketDataProvider} and
 * persists daily bars. Quote/fundamental gaps are tolerated (nullable);
 * a failed history fetch fails the bundle so callers can mark INSUFFICIENT_DATA.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final MarketDataProvider provider;
    private final MarketDataRepository repository;
    private final AppProperties properties;

    public MarketDataService(MarketDataProvider provider, MarketDataRepository repository,
                             AppProperties properties) {
        this.provider = provider;
        this.repository = repository;
        this.properties = properties;
    }

    public record MarketDataBundle(MarketQuote quote, List<HistoricalPrice> history,
                                   FundamentalData fundamentals) {}

    @Transactional
    public MarketDataBundle fetchAndStore(Stock stock) {
        LocalDate to = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        LocalDate from = to.minusDays(properties.getAnalysis().getHistoryDays());
        List<HistoricalPrice> history =
                provider.getHistoricalPrices(stock.getSymbol(), stock.getExchange(), from, to);
        int stored = 0;
        for (HistoricalPrice h : history) {
            if (!repository.existsByStockIdAndTradingDateAndDataSource(
                    stock.getId(), h.tradingDate(), provider.providerName())) {
                MarketDataPoint p = new MarketDataPoint();
                p.setStock(stock);
                p.setPrice(h.close());
                p.setOpenPrice(h.open());
                p.setHighPrice(h.high());
                p.setLowPrice(h.low());
                p.setClosePrice(h.close());
                p.setVolume(h.volume());
                p.setTradingDate(h.tradingDate());
                p.setDataSource(provider.providerName());
                repository.save(p);
                stored++;
            }
        }
        log.info("Stored {} new bars for {} ({})", stored, stock.getSymbol(), stock.getExchange());

        MarketQuote quote = null;
        try {
            quote = provider.getLatestQuote(stock.getSymbol(), stock.getExchange());
        } catch (ExternalProviderException e) {
            log.warn("Latest quote unavailable for {}: {}", stock.getSymbol(), e.getMessage());
        }
        FundamentalData fundamentals = null;
        try {
            fundamentals = provider.getFundamentals(stock.getSymbol(), stock.getExchange());
        } catch (Exception e) {
            log.warn("Fundamentals unavailable for {}: {}", stock.getSymbol(), e.getMessage());
        }
        politeDelay();
        return new MarketDataBundle(quote, history, fundamentals);
    }

    @Transactional(readOnly = true)
    public List<HistoricalPrice> recentHistory(Long stockId, LocalDate from, LocalDate to) {
        return repository.findByStockIdAndTradingDateBetweenOrderByTradingDateAsc(stockId, from, to).stream()
                .map(p -> new HistoricalPrice(p.getTradingDate(), p.getOpenPrice(), p.getHighPrice(),
                        p.getLowPrice(), p.getClosePrice(), p.getVolume()))
                .toList();
    }

    private void politeDelay() {
        try {
            Thread.sleep(properties.getAlphaVantage() == null ? 0 : requestDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long requestDelayMs() {
        // Gentle default spacing for free-tier per-minute caps; override via property if needed.
        return 1000L;
    }
}
