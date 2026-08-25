-- V23: Real-Time Juice Exchange Market Correlation and Market Events Schema Migration

-- 1. Create product_correlations table for product-to-product market relationships
CREATE TABLE IF NOT EXISTS product_correlations (
    id BIGSERIAL PRIMARY KEY,
    source_product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    target_product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    correlation_coefficient NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_source_target_correlation UNIQUE (source_product_id, target_product_id)
);

-- 2. Create market_events table for authoritative market activity logging
CREATE TABLE IF NOT EXISTS market_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    quantity INT DEFAULT 1,
    price_before NUMERIC(10, 2),
    price_after NUMERIC(10, 2),
    market_version INT NOT NULL,
    details TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create index for fast correlation lookups
CREATE INDEX IF NOT EXISTS idx_product_correlations_source ON product_correlations(source_product_id, enabled);

-- 4. Seed realistic correlation matrix between the active 8 products
INSERT INTO product_correlations (source_product_id, target_product_id, correlation_coefficient, enabled)
SELECT p1.id, p2.id, 
  CASE 
    WHEN p1.flavour = 'THUNDER' AND p2.flavour = 'MANGO' THEN 0.50
    WHEN p1.flavour = 'THUNDER' AND p2.flavour = 'ORANGE' THEN 0.50
    WHEN p1.flavour = 'THUNDER' AND p2.flavour = 'LYCHEE' THEN 0.25
    WHEN p1.flavour = 'THUNDER' AND p2.flavour = 'STRAWBERRY' THEN 0.25
    WHEN p1.flavour = 'MANGO' AND p2.flavour = 'STRAWBERRY' THEN 0.40
    WHEN p1.flavour = 'MANGO' AND p2.flavour = 'ORANGE' THEN 0.30
    WHEN p1.flavour = 'ORANGE' AND p2.flavour = 'LEMON' THEN 0.30
    WHEN p1.flavour = 'ORANGE' AND p2.flavour = 'MINT' THEN 0.20
    WHEN p1.flavour = 'LYCHEE' AND p2.flavour = 'GRAPE' THEN 0.30
    WHEN p1.flavour = 'LYCHEE' AND p2.flavour = 'MANGO' THEN 0.25
    WHEN p1.flavour = 'LEMON' AND p2.flavour = 'MINT' THEN 0.50
    WHEN p1.flavour = 'STRAWBERRY' AND p2.flavour = 'MANGO' THEN 0.35
    ELSE 0.10
  END,
  TRUE
FROM products p1, products p2
WHERE p1.id <> p2.id
  AND p1.is_active = TRUE AND p2.is_active = TRUE
ON CONFLICT (source_product_id, target_product_id) DO UPDATE SET
  correlation_coefficient = EXCLUDED.correlation_coefficient,
  enabled = TRUE;
