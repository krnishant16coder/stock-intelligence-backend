package com.stockintelligence.schedule;

import com.stockintelligence.watchlist.Watchlist;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "analysis_schedules")
public class AnalysisSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false, unique = true)
    private Watchlist watchlist;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Frequency frequency = Frequency.DAILY;

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(nullable = false)
    private boolean active = true;

    protected AnalysisSchedule() {}

    public AnalysisSchedule(Watchlist watchlist, Frequency frequency, String timezone, Instant nextRunAt) {
        this.watchlist = watchlist;
        this.frequency = frequency;
        this.timezone = timezone;
        this.nextRunAt = nextRunAt;
    }

    public Long getId() { return id; }
    public Watchlist getWatchlist() { return watchlist; }
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
