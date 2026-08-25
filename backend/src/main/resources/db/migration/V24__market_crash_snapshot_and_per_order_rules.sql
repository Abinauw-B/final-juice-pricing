-- V24: Market Crash Immutable Snapshot and Crash State Persistence
CREATE TABLE IF NOT EXISTS market_crash_snapshots (
    id BIGSERIAL PRIMARY KEY,
    crash_code VARCHAR(50) NOT NULL,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    pre_crash_price NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_crash_code_product UNIQUE (crash_code, product_id)
);

CREATE INDEX IF NOT EXISTS idx_crash_snapshots_code ON market_crash_snapshots(crash_code);
