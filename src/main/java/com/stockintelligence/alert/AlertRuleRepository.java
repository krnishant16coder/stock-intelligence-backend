package com.stockintelligence.alert;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByStockIdOrderByThresholdPctAsc(Long stockId);
    List<AlertRule> findByStockIdAndActiveTrue(Long stockId);
    List<AlertRule> findAllByOrderByIdAsc();
}
