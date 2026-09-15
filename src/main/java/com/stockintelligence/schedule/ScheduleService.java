package com.stockintelligence.schedule;

import com.stockintelligence.common.BadRequestException;
import com.stockintelligence.watchlist.Watchlist;
import com.stockintelligence.watchlist.WatchlistService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private final AnalysisScheduleRepository repository;
    private final WatchlistService watchlists;

    public ScheduleService(AnalysisScheduleRepository repository, WatchlistService watchlists) {
        this.repository = repository;
        this.watchlists = watchlists;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list() {
        return repository.findAll().stream().map(ScheduleResponse::from).toList();
    }

    /** Create-or-update the schedule for a watchlist (V1: one schedule per watchlist). */
    @Transactional
    public ScheduleResponse upsert(Long watchlistId, ScheduleRequest request) {
        Watchlist watchlist = watchlists.getOrThrow(watchlistId);
        Frequency frequency = request.frequency() != null ? request.frequency() : Frequency.DAILY;
        String timezone = (request.timezone() == null || request.timezone().isBlank())
                ? "Asia/Kolkata" : request.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new BadRequestException("Invalid timezone: " + timezone);
        }
        boolean active = request.active() == null || request.active();

        AnalysisSchedule schedule = repository.findByWatchlistId(watchlistId)
                .orElseGet(() -> new AnalysisSchedule(watchlist, frequency, timezone, Instant.now()));
        schedule.setFrequency(frequency);
        schedule.setTimezone(timezone);
        schedule.setActive(active);
        if (schedule.getNextRunAt() == null || schedule.getNextRunAt().isBefore(Instant.now().minusSeconds(3600))) {
            schedule.setNextRunAt(computeNextRun(frequency, ZoneId.of(timezone), Instant.now()));
        }
        return ScheduleResponse.from(repository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<AnalysisSchedule> dueSchedules(Instant now) {
        return repository.findByActiveTrueAndNextRunAtLessThanEqual(now);
    }

    @Transactional
    public void markExecuted(AnalysisSchedule schedule, Instant ranAt) {
        schedule.setLastRunAt(ranAt);
        ZoneId zone;
        try {
            zone = ZoneId.of(schedule.getTimezone());
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        schedule.setNextRunAt(computeNextRun(schedule.getFrequency(), zone, ranAt));
        repository.save(schedule);
    }

    /** Next run preserves the wall-clock time in the schedule's timezone. */
    public static Instant computeNextRun(Frequency frequency, ZoneId zone, Instant from) {
        ZonedDateTime zdt = from.atZone(zone);
        ZonedDateTime next = switch (frequency) {
            case DAILY -> zdt.plusDays(1);
            case WEEKLY -> zdt.plusWeeks(1);
            case MONTHLY -> zdt.plusMonths(1);
        };
        return next.toInstant();
    }
}
