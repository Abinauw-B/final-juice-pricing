-- V25: Dynamic Pricing Configuration System and Audit Trail
-- Creates persistent pricing configuration table, audit logs, and versioning

CREATE TABLE IF NOT EXISTS pricing_configurations (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    data_type VARCHAR(50) NOT NULL DEFAULT 'STRING', -- 'DECIMAL', 'INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING'
    scope VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',     -- 'GLOBAL' or 'PRODUCT'
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    description VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 1,
    updated_by VARCHAR(100) DEFAULT 'SYSTEM',
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pricing_config_key_product UNIQUE (setting_key, product_id)
);

CREATE TABLE IF NOT EXISTS pricing_config_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user VARCHAR(100) NOT NULL DEFAULT 'ADMIN',
    setting_key VARCHAR(100) NOT NULL,
    product_id BIGINT,
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    version_before BIGINT,
    version_after BIGINT,
    reason VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Extend price_history with config_version
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS config_version BIGINT DEFAULT 1;

-- Seed Global Baseline Pricing Configuration
INSERT INTO pricing_configurations (setting_key, setting_value, data_type, scope, description, version, updated_by)
VALUES
('SETTLEMENT_INTERVAL_SECONDS', '120', 'INTEGER', 'GLOBAL', 'DWMA Pricing Settlement Interval in seconds', 1, 'SYSTEM'),
('WEIGHT_W0', '1.0000', 'DECIMAL', 'GLOBAL', 'Weight for current window W0 [now-2m, now)', 1, 'SYSTEM'),
('WEIGHT_W1', '0.5000', 'DECIMAL', 'GLOBAL', 'Weight for previous window W1 [now-4m, now-2m)', 1, 'SYSTEM'),
('WEIGHT_W2', '0.2500', 'DECIMAL', 'GLOBAL', 'Weight for older window W2 [now-6m, now-4m)', 1, 'SYSTEM'),
('HIGH_DEMAND_THRESHOLD', '1.1000', 'DECIMAL', 'GLOBAL', 'Demand ratio threshold for price surge (+step)', 1, 'SYSTEM'),
('STABLE_DEMAND_LOWER_THRESHOLD', '0.9000', 'DECIMAL', 'GLOBAL', 'Lower demand ratio bound for holding price (₹0)', 1, 'SYSTEM'),
('STABLE_DEMAND_UPPER_THRESHOLD', '1.1000', 'DECIMAL', 'GLOBAL', 'Upper demand ratio bound for holding price (₹0)', 1, 'SYSTEM'),
('LOW_DEMAND_THRESHOLD', '0.5000', 'DECIMAL', 'GLOBAL', 'Threshold distinguishing minor decay vs zero-demand decay', 1, 'SYSTEM'),
('INCREASE_STEP', '1.00', 'DECIMAL', 'GLOBAL', 'Monetary amount added on high demand surge (₹)', 1, 'SYSTEM'),
('DECREASE_STEP_1', '1.00', 'DECIMAL', 'GLOBAL', 'Monetary amount deducted on below normal demand (₹)', 1, 'SYSTEM'),
('DECREASE_STEP_2', '2.00', 'DECIMAL', 'GLOBAL', 'Monetary amount deducted on zero/extreme low demand (₹)', 1, 'SYSTEM'),
('MARKET_CRASH_DURATION_SECONDS', '180', 'INTEGER', 'GLOBAL', 'Market crash duration in seconds', 1, 'SYSTEM'),
('MARKET_CRASH_PRICE', '18.00', 'DECIMAL', 'GLOBAL', 'Market crash floor override price (₹)', 1, 'SYSTEM'),
('DEFAULT_CUP_PRICE', '25.00', 'DECIMAL', 'GLOBAL', 'Default base price for standard products (₹)', 1, 'SYSTEM'),
('MIN_CUP_PRICE', '18.00', 'DECIMAL', 'GLOBAL', 'Global hard floor limit (₹)', 1, 'SYSTEM'),
('MAX_CUP_PRICE', '35.00', 'DECIMAL', 'GLOBAL', 'Global hard ceiling limit (₹)', 1, 'SYSTEM'),
('GLOBAL_CONFIG_VERSION', '1', 'INTEGER', 'GLOBAL', 'Monotonically increasing configuration revision', 1, 'SYSTEM')
ON CONFLICT (setting_key, product_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_pricing_config_key ON pricing_configurations(setting_key);
CREATE INDEX IF NOT EXISTS idx_pricing_config_product ON pricing_configurations(product_id);
CREATE INDEX IF NOT EXISTS idx_pricing_config_audit_key ON pricing_config_audit_logs(setting_key, created_at DESC);
