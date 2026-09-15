package com.stockintelligence.stock;

import com.stockintelligence.common.BadRequestException;
import com.stockintelligence.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockRepository repository;

    public StockService(StockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StockResponse create(StockRequest request) {
        String symbol = request.symbol().toUpperCase().trim();
        String exchange = request.exchange().toUpperCase().trim();
        if (repository.existsBySymbolAndExchange(symbol, exchange)) {
            throw new BadRequestException("Stock already exists: " + symbol + " (" + exchange + ")");
        }
        Stock saved = repository.save(new Stock(symbol, request.companyName().trim(), exchange));
        return StockResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> list() {
        return repository.findAllByOrderByCompanyNameAsc().stream().map(StockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Stock getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + id));
    }

    @Transactional
    public StockResponse update(Long id, StockRequest request) {
        Stock stock = getOrThrow(id);
        String symbol = request.symbol().toUpperCase().trim();
        String exchange = request.exchange().toUpperCase().trim();
        repository.findBySymbolAndExchange(symbol, exchange)
                .filter(s -> !s.getId().equals(id))
                .ifPresent(s -> { throw new BadRequestException("Another stock uses " + symbol + " (" + exchange + ")"); });
        stock.setSymbol(symbol);
        stock.setCompanyName(request.companyName().trim());
        stock.setExchange(exchange);
        return StockResponse.from(repository.save(stock));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Stock not found: " + id);
        }
        repository.deleteById(id);
    }
}
