package com.stockintelligence.alert;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public List<AlertResponse> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) Long stockId) {
        return service.list(status, stockId);
    }

    @GetMapping("/{id}")
    public AlertResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}/read")
    public AlertResponse markRead(@PathVariable Long id) {
        return service.markRead(id);
    }
}
