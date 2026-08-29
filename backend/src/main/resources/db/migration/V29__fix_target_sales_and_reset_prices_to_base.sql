-- V29: Fix corrupted target_sales_per_1_minute values, reset prices to base, and restore 60s settlement interval
UPDATE products SET target_sales_per_1_minute = 0.55, target_sales_per_2_minute = 1.10 WHERE flavour = 'FRESH_MANGO_JUICE';
UPDATE products SET target_sales_per_1_minute = 0.40, target_sales_per_2_minute = 0.80 WHERE flavour = 'ZESTY_LEMON_JUICE';
UPDATE products SET target_sales_per_1_minute = 0.30, target_sales_per_2_minute = 0.60 WHERE flavour = 'COOL_MINT_COOLER';
UPDATE products SET target_sales_per_1_minute = 0.45, target_sales_per_2_minute = 0.90 WHERE flavour = 'VALENCIA_ORANGE_JUICE';
UPDATE products SET target_sales_per_1_minute = 0.35, target_sales_per_2_minute = 0.70 WHERE flavour = 'STRAWBERRY_DELIGHT';
UPDATE products SET target_sales_per_1_minute = 0.55, target_sales_per_2_minute = 1.10 WHERE flavour = 'ROYAL_GRAPE_JUICE';
UPDATE products SET target_sales_per_1_minute = 0.45, target_sales_per_2_minute = 0.90 WHERE flavour = 'LYCHEE_MIST';
UPDATE products SET target_sales_per_1_minute = 0.50, target_sales_per_2_minute = 1.00 WHERE flavour = 'THUNDER';
UPDATE products SET target_sales_per_1_minute = 0.50, target_sales_per_2_minute = 1.00 WHERE flavour = 'MANGO';
UPDATE products SET target_sales_per_1_minute = 0.55, target_sales_per_2_minute = 1.10 WHERE flavour = 'ORANGE';
UPDATE products SET target_sales_per_1_minute = 0.40, target_sales_per_2_minute = 0.80 WHERE flavour = 'MINT';
UPDATE products SET current_cup_price = default_cup_price, pricing_mode = 'DYNAMIC', price_version = COALESCE(price_version, 0) + 1, last_price_change_timestamp = CURRENT_TIMESTAMP WHERE is_active = true AND current_cup_price <= 18.50;
UPDATE pricing_configurations SET setting_value = '60', updated_by = 'SYSTEM_V29_MIGRATION', updated_at = CURRENT_TIMESTAMP WHERE setting_key = 'SETTLEMENT_INTERVAL_SECONDS' AND product_id IS NULL;
UPDATE pricing_configurations SET setting_value = (COALESCE(setting_value::bigint, 1) + 1)::text, updated_at = CURRENT_TIMESTAMP WHERE setting_key = 'GLOBAL_CONFIG_VERSION' AND product_id IS NULL;
