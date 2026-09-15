package com.stockintelligence.alert;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    boolean existsByDedupKey(String dedupKey);
    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);
    List<Alert> findByStockIdOrderByCreatedAtDesc(Long stockId);
    List<Alert> findAllByOrderByCreatedAtDesc();
}
