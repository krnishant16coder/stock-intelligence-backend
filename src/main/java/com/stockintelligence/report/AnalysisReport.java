package com.stockintelligence.report;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "analysis_reports")
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "stocks_analyzed")
    private int stocksAnalyzed;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 16)
    private TriggerType triggerType = TriggerType.MANUAL;

    protected AnalysisReport() {}

    public Long getId() { return id; }
    public Watchlist getWatchlist() { return watchlist; }
    public void setWatchlist(Watchlist watchlist) { this.watchlist = watchlist; }
    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }
    public Instant getGeneratedAt() { return generatedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public int getStocksAnalyzed() { return stocksAnalyzed; }
    public void setStocksAnalyzed(int stocksAnalyzed) { this.stocksAnalyzed = stocksAnalyzed; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }

    @PrePersist
    void onCreate() {
        generatedAt = Instant.now();
    }
}
