package com.stockintelligence.watchlist;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistService service;

    public WatchlistController(WatchlistService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistResponse create(@RequestBody Map<String, String> body) {
        return service.create(body.getOrDefault("name", ""));
    }

    @GetMapping
    public List<WatchlistResponse> list() {
        return service.list();
    }

    @PostMapping("/{id}/stocks")
    public WatchlistResponse addStock(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long stockId = body.get("stockId");
        if (stockId == null) {
            throw new com.stockintelligence.common.BadRequestException("stockId is required");
        }
        return service.addStock(id, stockId);
    }

    @DeleteMapping("/{id}/stocks/{stockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStock(@PathVariable Long id, @PathVariable Long stockId) {
        service.removeStock(id, stockId);
    }
}
