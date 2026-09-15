package com.stockintelligence.schedule;

import java.time.Instant;

public record ScheduleResponse(Long id, Long watchlistId, Frequency frequency, String timezone,
                               Instant nextRunAt, Instant lastRunAt, boolean active) {
    public static ScheduleResponse from(AnalysisSchedule s) {
        return new ScheduleResponse(s.getId(), s.getWatchlist().getId(), s.getFrequency(),
                s.getTimezone(), s.getNextRunAt(), s.getLastRunAt(), s.isActive());
    }
}
