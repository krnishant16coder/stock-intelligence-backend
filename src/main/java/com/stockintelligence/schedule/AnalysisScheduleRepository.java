package com.stockintelligence.schedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisScheduleRepository extends JpaRepository<AnalysisSchedule, Long> {
    Optional<AnalysisSchedule> findByWatchlistId(Long watchlistId);
    List<AnalysisSchedule> findByActiveTrueAndNextRunAtLessThanEqual(Instant now);
}
