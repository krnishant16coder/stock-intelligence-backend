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
 * Fetches market data through all available {@link MarketDataProvider}s and
 * persists daily bars. Yahoo (no key, generous limits) is tried first for
 * history/quote so every stock gets real data even when the keyed provider
 * (Alpha Vantage free: ~25 req/day, 3 req/stock) is rate-limited; the
 * configured provider stays as fallback. Fundamentals are attempted
 * configured-first (richest fields), Yahoo-last (52-week range + currency).
 * Quote/fundamental gaps are tolerated (nullable); history failing on every
 * provider fails the bundle so callers can fall back to persisted bars.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final List<MarketDataProvider> historyProviders;
    private final List<MarketDataProvider> fundamentalProviders;
    private final MarketDataRepository repository;
    private final AppProperties properties;

    public MarketDataService(List<MarketDataProvider> providers, MarketDataRepository repository,
                             AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
        String configured = properties.getMarketDataProvider();
        // History/quote: free Yahoo first, configured provider as fallback.
        this.historyProviders = providers.stream()
                .sorted((a, b) -> Integer.compare(historyRank(a, configured), historyRank(b, configured)))
                .toList();
        // Fundamentals: configured provider first, Yahoo last (52w/currency only).
        this.fundamentalProviders = providers.stream()
                .sorted((a, b) -> Integer.compare(fundamentalRank(a, configured), fundamentalRank(b, configured)))
                .toList();
        log.info("Market-data provider order: history={} fundamentals={}",
                this.historyProviders.stream().map(MarketDataProvider::providerName).toList(),
                this.fundamentalProviders.stream().map(MarketDataProvider::providerName).toList());
    }

    private static int historyRank(MarketDataProvider p, String configured) {
        if ("yahoo".equals(p.providerName())) {
            return 0;
        }
        return p.providerName().equals(configured) ? 1 : 2;
    }

    private static int fundamentalRank(MarketDataProvider p, String configured) {
        if (p.providerName().equals(configured)) {
            return 0;
        }
        return "yahoo".equals(p.providerName()) ? 2 : 1;
    }

    public record MarketDataBundle(MarketQuote quote, List<HistoricalPrice> history,
                                   FundamentalData fundamentals) {}

    @Transactional
    public MarketDataBundle fetchAndStore(Stock stock) {
        LocalDate to = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        LocalDate from = to.minusDays(properties.getAnalysis().getHistoryDays());
        List<HistoricalPrice> history = null;
        String source = null;
        ExternalProviderException lastError = null;
        for (MarketDataProvider p : historyProviders) {
            try {
                List<HistoricalPrice> bars =
                        p.getHistoricalPrices(stock.getSymbol(), stock.getExchange(), from, to);
                if (bars != null && !bars.isEmpty()) {
                    history = bars;
                    source = p.providerName();
                    break;
                }
            } catch (ExternalProviderException e) {
                lastError = e;
                log.warn("History via {} failed for {} ({}), trying next provider: {}",
                        p.providerName(), stock.getSymbol(), stock.getExchange(), e.getMessage());
            }
        }
        if (history == null) {
            throw lastError != null ? lastError
                    : new ExternalProviderException(
                            "No market-data provider returned history for " + stock.getSymbol());
        }
        int stored = 0;
        for (HistoricalPrice h : history) {
            if (!repository.existsByStockIdAndTradingDateAndDataSource(
                    stock.getId(), h.tradingDate(), source)) {
                MarketDataPoint p = new MarketDataPoint();
                p.setStock(stock);
                p.setPrice(h.close());
                p.setOpenPrice(h.open());
                p.setHighPrice(h.high());
                p.setLowPrice(h.low());
                p.setClosePrice(h.close());
                p.setVolume(h.volume());
                p.setTradingDate(h.tradingDate());
                p.setDataSource(source);
                repository.save(p);
                stored++;
            }
        }
        log.info("Stored {} new bars for {} ({}) via {}", stored, stock.getSymbol(), stock.getExchange(), source);

        MarketQuote quote = null;
        for (MarketDataProvider p : historyProviders) {
            try {
                quote = p.getLatestQuote(stock.getSymbol(), stock.getExchange());
                if (quote != null) {
                    break;
                }
            } catch (ExternalProviderException e) {
                log.warn("Latest quote via {} unavailable for {}: {}",
                        p.providerName(), stock.getSymbol(), e.getMessage());
            }
        }
        FundamentalData fundamentals = null;
        for (MarketDataProvider p : fundamentalProviders) {
            try {
                fundamentals = p.getFundamentals(stock.getSymbol(), stock.getExchange());
                if (fundamentals != null) {
                    break;
                }
            } catch (Exception e) {
                log.warn("Fundamentals via {} unavailable for {}: {}",
                        p.providerName(), stock.getSymbol(), e.getMessage());
            }
        }
        if (fundamentals == null) {
            log.info("Fundamentals unavailable for {} ({}); AI should infer from price/news, not UNKNOWN",
                    stock.getSymbol(), stock.getExchange());
        }
        politeDelay("yahoo".equals(source));
        return new MarketDataBundle(quote, history, fundamentals);
    }

    @Transactional(readOnly = true)
    public List<HistoricalPrice> recentHistory(Long stockId, LocalDate from, LocalDate to) {
        return repository.findByStockIdAndTradingDateBetweenOrderByTradingDateAsc(stockId, from, to).stream()
                .map(p -> new HistoricalPrice(p.getTradingDate(), p.getOpenPrice(), p.getHighPrice(),
                        p.getLowPrice(), p.getClosePrice(), p.getVolume()))
                .toList();
    }

    private void politeDelay(boolean freeProvider) {
        try {
            Thread.sleep(freeProvider ? 300L : requestDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long requestDelayMs() {
        // Gentle default spacing for free-tier per-minute caps; override via property if needed.
        return 1000L;
    }
}
