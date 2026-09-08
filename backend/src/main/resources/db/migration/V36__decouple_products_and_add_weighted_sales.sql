-- V36: Add weighted_sales to products and decouple all cross-product market correlations
-- 1. Add weighted_sales column to products table if not exists
ALTER TABLE products ADD COLUMN IF NOT EXISTS weighted_sales NUMERIC(10, 2) DEFAULT 0.55;

-- 2. Populate initial weighted_sales from target_sales_per_1_minute
UPDATE products 
SET weighted_sales = COALESCE(target_sales_per_1_minute, 0.55)
WHERE weighted_sales IS NULL;

-- 3. Decouple all product correlations completely: no product is linked with any other
UPDATE product_correlations
SET enabled = FALSE,
    correlation_coefficient = 0.00,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Bump global config version
UPDATE pricing_configurations
SET setting_value = (COALESCE(setting_value::bigint, 1) + 1)::text, updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'GLOBAL_CONFIG_VERSION' AND product_id IS NULL;
