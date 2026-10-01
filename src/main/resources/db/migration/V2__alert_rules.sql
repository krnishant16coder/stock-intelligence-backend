CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    threshold_pct DOUBLE PRECISION NOT NULL,
    direction VARCHAR(8) NOT NULL DEFAULT 'BOTH',
    severity VARCHAR(16) NOT NULL DEFAULT 'HIGH',
    cooldown_hours INTEGER NOT NULL DEFAULT 24,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_alert_rules_stock ON alert_rules(stock_id);
CREATE INDEX IF NOT EXISTS ix_alert_rules_active ON alert_rules(active);
