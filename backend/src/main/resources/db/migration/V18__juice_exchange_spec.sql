-- V18: Juice Exchange Dynamic Pricing Spec Migration
-- Updates schema for 8-juice exchange mechanics, weighted sales auditing, and settlement tracking

-- 1. Extend products table with target_sales_per_2_minute if not exists
ALTER TABLE products ADD COLUMN IF NOT EXISTS target_sales_per_2_minute DOUBLE PRECISION DEFAULT 1.00;

-- 2. Update existing products and insert missing products to ensure standard 8 juices start at ₹25 (Floor ₹18, Ceiling ₹35)
UPDATE products SET
    default_cup_price = 25.00,
    current_cup_price = 25.00,
    min_cup_price = 18.00,
    max_cup_price = 35.00,
    target_sales_per_2_minute = 1.00;

INSERT INTO products (name, flavour, description, default_cup_size_ml, default_cup_price, current_cup_price, min_cup_price, max_cup_price, target_sales_per_2_minute)
VALUES
('Fresh Mango Juice', 'MANGO', 'Sweet fresh Alphonso mango pulp juice', 250, 25.00, 25.00, 18.00, 35.00, 1.10),
('Valencia Orange Juice', 'ORANGE', 'Pure Valencia orange juice loaded with vitamin C', 250, 25.00, 25.00, 18.00, 35.00, 1.10),
('Watermelon Splash', 'WATERMELON', 'Chilled fresh watermelon nectar', 250, 25.00, 25.00, 18.00, 35.00, 0.90),
('Pineapple Express', 'PINEAPPLE', 'Fresh tropical crushed pineapple nectar', 250, 25.00, 25.00, 18.00, 35.00, 0.90),
('Fresh Mosambi Juice', 'MOSAMBI', 'Tangy and sweet sweet lime juice', 250, 25.00, 25.00, 18.00, 35.00, 0.80),
('Royal Apple Juice', 'APPLE', 'Crisp fresh red apple juice', 250, 25.00, 25.00, 18.00, 35.00, 0.70),
('Pomegranate Burst', 'POMEGRANATE', 'Rich antioxidant pomegranate extract', 250, 25.00, 25.00, 18.00, 35.00, 0.70),
('Mixed Fruit Punch', 'MIXED_FRUIT', 'Delicious blend of seasonal fresh fruits', 250, 25.00, 25.00, 18.00, 35.00, 0.90)
ON CONFLICT (flavour) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    default_cup_price = 25.00,
    current_cup_price = 25.00,
    min_cup_price = 18.00,
    max_cup_price = 35.00,
    target_sales_per_2_minute = EXCLUDED.target_sales_per_2_minute;

-- 3. Extend price_history with audit columns for weighted demand calculations
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS price_change NUMERIC(10,2);
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS demand_ratio DOUBLE PRECISION;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS weighted_sales DOUBLE PRECISION;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS target_sales DOUBLE PRECISION;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS calculation_window_start TIMESTAMP;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS calculation_window_end TIMESTAMP;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS reason VARCHAR(100);

-- 4. Create juice_market_settlements table for tracking 2-minute settlement windows
CREATE TABLE IF NOT EXISTS juice_market_settlements (
    id BIGSERIAL PRIMARY KEY,
    settlement_window_start TIMESTAMP NOT NULL,
    settlement_window_end TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(50) DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Create juice_market_crashes table for logging market crash events
CREATE TABLE IF NOT EXISTS juice_market_crashes (
    id BIGSERIAL PRIMARY KEY,
    crash_code VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP,
    affected_product_ids VARCHAR(255),
    crash_price NUMERIC(10,2) DEFAULT 18.00,
    trigger_type VARCHAR(50) DEFAULT 'MANUAL_ADMIN',
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_settlement_idempotency ON juice_market_settlements(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_price_history_product_created ON price_history(product_id, created_at DESC);
