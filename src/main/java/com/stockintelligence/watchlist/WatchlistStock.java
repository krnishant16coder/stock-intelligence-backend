package com.stockintelligence.watchlist;

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
import java.time.Instant;

@Entity
@Table(name = "watchlist_stocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"watchlist_id", "stock_id"}))
public class WatchlistStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected WatchlistStock() {}

    public WatchlistStock(Watchlist watchlist, Stock stock) {
        this.watchlist = watchlist;
        this.stock = stock;
    }

    @PrePersist
    void onCreate() {
        addedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Watchlist getWatchlist() { return watchlist; }
    public Stock getStock() { return stock; }
    public Instant getAddedAt() { return addedAt; }
}
