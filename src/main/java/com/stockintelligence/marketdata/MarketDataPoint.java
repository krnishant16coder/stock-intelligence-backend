package com.stockintelligence.marketdata;

import com.stockintelligence.stock.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_data",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "trading_date", "data_source"}))
public class MarketDataPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 19, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 19, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", precision = 19, scale = 4)
    private BigDecimal closePrice;

    private Long volume;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(name = "data_source", nullable = false, length = 32)
    private String dataSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MarketDataPoint() {}

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public void setStock(Stock stock) { this.stock = stock; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public void setOpenPrice(BigDecimal v) { this.openPrice = v; }
    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal v) { this.highPrice = v; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal v) { this.lowPrice = v; }
    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal v) { this.closePrice = v; }
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
    public LocalDate getTradingDate() { return tradingDate; }
    public void setTradingDate(LocalDate tradingDate) { this.tradingDate = tradingDate; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
