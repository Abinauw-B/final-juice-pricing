-- V26: Migrate Dynamic Pricing Engine to 1-Minute Settlement Cycle with ₹4 Downward Steps

-- 1. Add target_sales_per_1_minute column to products table
ALTER TABLE products ADD COLUMN IF NOT EXISTS target_sales_per_1_minute DOUBLE PRECISION DEFAULT 0.55;

-- 2. Migrate existing 2-minute target sales values to 1-minute equivalent (divided by 2)
UPDATE products
SET target_sales_per_1_minute = ROUND((COALESCE(target_sales_per_2_minute, 1.0) / 2.0)::numeric, 2)
WHERE target_sales_per_1_minute IS NULL OR target_sales_per_1_minute = 0.55;

-- Set exact authoritative 1-minute targets for the 8 catalog products if present
UPDATE products SET target_sales_per_1_minute = 0.55 WHERE flavour = 'MANGO';
UPDATE products SET target_sales_per_1_minute = 0.40 WHERE flavour = 'LEMON';
UPDATE products SET target_sales_per_1_minute = 0.55 WHERE flavour = 'ORANGE';
UPDATE products SET target_sales_per_1_minute = 0.40 WHERE flavour = 'MINT';
UPDATE products SET target_sales_per_1_minute = 0.45 WHERE flavour = 'GRAPE';
UPDATE products SET target_sales_per_1_minute = 0.35 WHERE flavour = 'STRAWBERRY';
UPDATE products SET target_sales_per_1_minute = 0.45 WHERE flavour = 'THUNDER';
UPDATE products SET target_sales_per_1_minute = 0.45 WHERE flavour = 'LYCHEE';

-- 3. Update global pricing configuration settings in pricing_configurations
UPDATE pricing_configurations
SET setting_value = '60', updated_by = 'SYSTEM_V26_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'SETTLEMENT_INTERVAL_SECONDS' AND product_id IS NULL;

UPDATE pricing_configurations
SET setting_value = '4.00', updated_by = 'SYSTEM_V26_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'DECREASE_STEP_1' AND product_id IS NULL;

UPDATE pricing_configurations
SET setting_value = '4.00', updated_by = 'SYSTEM_V26_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'DECREASE_STEP_2' AND product_id IS NULL;

-- Insert PRICE_DECREASE_STEP setting key for authoritative global configuration
INSERT INTO pricing_configurations (setting_key, setting_value, data_type, scope, description, version, updated_by)
VALUES ('PRICE_DECREASE_STEP', '4.00', 'DECIMAL', 'GLOBAL', 'Base downward price step in ₹ on low/zero demand', 1, 'SYSTEM_V26_MIGRATION')
ON CONFLICT (setting_key, product_id) DO UPDATE SET setting_value = '4.00', updated_at = CURRENT_TIMESTAMP;

-- Bump global config version
UPDATE pricing_configurations
SET setting_value = (COALESCE(setting_value::bigint, 1) + 1)::text, updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'GLOBAL_CONFIG_VERSION' AND product_id IS NULL;
