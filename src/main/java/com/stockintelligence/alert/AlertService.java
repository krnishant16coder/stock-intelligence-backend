package com.stockintelligence.alert;

import com.stockintelligence.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final AlertRepository repository;

    public AlertService(AlertRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(String status, Long stockId) {
        if (stockId != null) {
            return repository.findByStockIdOrderByCreatedAtDesc(stockId).stream()
                    .filter(a -> status == null || a.getStatus().name().equalsIgnoreCase(status))
                    .map(AlertResponse::from).toList();
        }
        if (status != null && !status.isBlank()) {
            AlertStatus s;
            try {
                s = AlertStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new com.stockintelligence.common.BadRequestException("Invalid status: " + status);
            }
            return repository.findByStatusOrderByCreatedAtDesc(s).stream().map(AlertResponse::from).toList();
        }
        return repository.findAllByOrderByCreatedAtDesc().stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse get(Long id) {
        return AlertResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id)));
    }

    @Transactional
    public AlertResponse markRead(Long id) {
        Alert alert = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));
        alert.setStatus(AlertStatus.READ);
        return AlertResponse.from(repository.save(alert));
    }
}
