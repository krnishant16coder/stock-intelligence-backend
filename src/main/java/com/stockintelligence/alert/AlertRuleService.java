package com.stockintelligence.alert;

import com.stockintelligence.common.BadRequestException;
import com.stockintelligence.common.ResourceNotFoundException;
import com.stockintelligence.stock.Stock;
import com.stockintelligence.stock.StockRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertRuleService {

    private final AlertRuleRepository repository;
    private final StockRepository stocks;

    public AlertRuleService(AlertRuleRepository repository, StockRepository stocks) {
        this.repository = repository;
        this.stocks = stocks;
    }

    @Transactional
    public AlertRuleResponse create(AlertRuleRequest request) {
        Stock stock = stocks.findById(request.stockId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + request.stockId()));
        AlertRule rule = new AlertRule();
        rule.setStock(stock);
        requireThreshold(request.thresholdPct());
        rule.setThresholdPct(request.thresholdPct());
        rule.setDirection(parseDirection(request.direction(), PriceDirection.BOTH));
        rule.setSeverity(parseSeverity(request.severity(), Severity.HIGH));
        rule.setCooldownHours(cooldownOrDefault(request.cooldownHours()));
        rule.setActive(request.active() == null || request.active());
        return AlertRuleResponse.from(repository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> list(Long stockId) {
        if (stockId != null) {
            stocks.findById(stockId)
                    .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + stockId));
            return repository.findByStockIdOrderByThresholdPctAsc(stockId).stream()
                    .map(AlertRuleResponse::from).toList();
        }
        return repository.findAllByOrderByIdAsc().stream().map(AlertRuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AlertRuleResponse get(Long id) {
        return AlertRuleResponse.from(findOrThrow(id));
    }

    @Transactional
    public AlertRuleResponse update(Long id, AlertRuleUpdate update) {
        AlertRule rule = findOrThrow(id);
        if (update.thresholdPct() != null) {
            requireThreshold(update.thresholdPct());
            rule.setThresholdPct(update.thresholdPct());
        }
        if (update.direction() != null) {
            rule.setDirection(parseDirection(update.direction(), rule.getDirection()));
        }
        if (update.severity() != null) {
            rule.setSeverity(parseSeverity(update.severity(), rule.getSeverity()));
        }
        if (update.cooldownHours() != null) {
            rule.setCooldownHours(cooldownOrDefault(update.cooldownHours()));
        }
        if (update.active() != null) {
            rule.setActive(update.active());
        }
        return AlertRuleResponse.from(repository.save(rule));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private AlertRule findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert rule not found: " + id));
    }

    private static void requireThreshold(double thresholdPct) {
        if (thresholdPct < 0.1 || thresholdPct > 100) {
            throw new BadRequestException("thresholdPct must be between 0.1 and 100");
        }
    }

    private static PriceDirection parseDirection(String raw, PriceDirection fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return PriceDirection.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid direction (UP, DOWN, BOTH): " + raw);
        }
    }

    private static Severity parseSeverity(String raw, Severity fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Severity.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid severity (LOW, MEDIUM, HIGH, CRITICAL): " + raw);
        }
    }

    private static int cooldownOrDefault(Integer cooldownHours) {
        if (cooldownHours == null) {
            return 24;
        }
        if (cooldownHours < 1) {
            throw new BadRequestException("cooldownHours must be >= 1");
        }
        return cooldownHours;
    }
}
