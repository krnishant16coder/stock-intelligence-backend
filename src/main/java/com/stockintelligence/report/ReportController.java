package com.stockintelligence.report;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/reports")
    public List<ReportResponse> list(@RequestParam(required = false) Long watchlistId) {
        return service.list(watchlistId);
    }

    @GetMapping("/reports/{id}")
    public ReportResponse get(@PathVariable Long id) {
        return service.getReport(id);
    }

    @GetMapping("/stocks/{id}/analysis")
    public List<StockAnalysisResponse> latestForStock(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "10") int limit) {
        return service.latestForStock(id, limit);
    }
}
