-- V1 schema for Stock Intelligence backend.
-- JSON-ish payloads use TEXT for portability (JSONB can be adopted later).

CREATE TABLE IF NOT EXISTS stocks (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    exchange VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_stocks_symbol_exchange UNIQUE (symbol, exchange)
);

CREATE TABLE IF NOT EXISTS watchlists (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS watchlist_stocks (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_watchlist_stock UNIQUE (watchlist_id, stock_id)
);
CREATE INDEX IF NOT EXISTS ix_watchlist_stocks_watchlist ON watchlist_stocks(watchlist_id);

CREATE TABLE IF NOT EXISTS analysis_schedules (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL UNIQUE REFERENCES watchlists(id) ON DELETE CASCADE,
    frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    next_run_at TIMESTAMPTZ,
    last_run_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS market_data (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    price NUMERIC(19,4),
    open_price NUMERIC(19,4),
    high_price NUMERIC(19,4),
    low_price NUMERIC(19,4),
    close_price NUMERIC(19,4),
    volume BIGINT,
    trading_date DATE NOT NULL,
    data_source VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_market_data_bar UNIQUE (stock_id, trading_date, data_source)
);
CREATE INDEX IF NOT EXISTS ix_market_data_stock_date ON market_data(stock_id, trading_date);

CREATE TABLE IF NOT EXISTS news_articles (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT REFERENCES stocks(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    summary TEXT,
    source VARCHAR(128),
    url TEXT,
    published_at TIMESTAMPTZ,
    data_source VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_news_stock_published ON news_articles(stock_id, published_at DESC);

CREATE TABLE IF NOT EXISTS analysis_reports (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    summary TEXT,
    stocks_analyzed INTEGER NOT NULL DEFAULT 0,
    trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
);
CREATE INDEX IF NOT EXISTS ix_reports_watchlist ON analysis_reports(watchlist_id, generated_at DESC);

CREATE TABLE IF NOT EXISTS stock_analysis (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES analysis_reports(id) ON DELETE CASCADE,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    signal VARCHAR(32),
    risk_level VARCHAR(16),
    price_trend VARCHAR(64),
    fundamental_trend VARCHAR(64),
    news_impact VARCHAR(64),
    summary TEXT,
    key_reasons TEXT,
    confidence DOUBLE PRECISION,
    critical_alert BOOLEAN NOT NULL DEFAULT FALSE,
    rule_metrics TEXT
);
CREATE INDEX IF NOT EXISTS ix_stock_analysis_report ON stock_analysis(report_id);
CREATE INDEX IF NOT EXISTS ix_stock_analysis_stock ON stock_analysis(stock_id);

CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    alert_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    dedup_key VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS ix_alerts_status_created ON alerts(status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_alerts_stock ON alerts(stock_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_alerts_dedup ON alerts(dedup_key);

CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT REFERENCES alerts(id) ON DELETE SET NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'EMAIL',
    recipient VARCHAR(255),
    subject TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL,
    error_message TEXT
);
