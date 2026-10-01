package com.stockintelligence.alert;

import com.stockintelligence.stock.Stock;
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

/**
 * User-defined price tripwire: mail me when this stock moves at least
 * {@code thresholdPct} in the given direction. Breaches create alerts with the
 * rule's severity (HIGH by default, so they mail instantly like other HIGHs).
 */
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "threshold_pct", nullable = false)
    private double thresholdPct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PriceDirection direction = PriceDirection.BOTH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity = Severity.HIGH;

    @Column(name = "cooldown_hours", nullable = false)
    private int cooldownHours = 24;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AlertRule() {}

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public void setStock(Stock stock) { this.stock = stock; }
    public double getThresholdPct() { return thresholdPct; }
    public void setThresholdPct(double thresholdPct) { this.thresholdPct = thresholdPct; }
    public PriceDirection getDirection() { return direction; }
    public void setDirection(PriceDirection direction) { this.direction = direction; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public int getCooldownHours() { return cooldownHours; }
    public void setCooldownHours(int cooldownHours) { this.cooldownHours = cooldownHours; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(Instant lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
