package com.stockintelligence.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "stocks", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "exchange"}))
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(nullable = false, length = 16)
    private String exchange;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Stock() {}

    public Stock(String symbol, String companyName, String exchange) {
        this.symbol = symbol.toUpperCase().trim();
        this.companyName = companyName;
        this.exchange = exchange.toUpperCase().trim();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol == null ? null : symbol.toUpperCase().trim(); }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange == null ? null : exchange.toUpperCase().trim(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
