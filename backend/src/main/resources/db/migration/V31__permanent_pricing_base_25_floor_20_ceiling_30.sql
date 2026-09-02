-- V31: Permanently enforce Base: ₹25.00, Floor: ₹20.00, Ceiling: ₹30.00 across all beverage products and pricing configs

-- 1. Update all active and catalog products to Base: 25.00, Floor: 20.00, Ceiling: 30.00
UPDATE products 
SET default_cup_price = 25.00,
    min_cup_price = 20.00,
    max_cup_price = 30.00,
    current_cup_price = 25.00,
    pricing_mode = 'DYNAMIC',
    price_version = COALESCE(price_version, 0) + 1,
    last_price_change_timestamp = CURRENT_TIMESTAMP
WHERE is_active = true;

-- 2. Update global pricing configuration key-values
UPDATE pricing_configurations 
SET setting_value = '25.00', updated_by = 'SYSTEM_V31_MIGRATION', updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'DEFAULT_CUP_PRICE' AND product_id IS NULL;

UPDATE pricing_configurations 
SET setting_value = '20.00', updated_by = 'SYSTEM_V31_MIGRATION', updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'MIN_CUP_PRICE' AND product_id IS NULL;

UPDATE pricing_configurations 
SET setting_value = '30.00', updated_by = 'SYSTEM_V31_MIGRATION', updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'MAX_CUP_PRICE' AND product_id IS NULL;

UPDATE pricing_configurations 
SET setting_value = '20.00', updated_by = 'SYSTEM_V31_MIGRATION', updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'MARKET_CRASH_PRICE' AND product_id IS NULL;

-- 3. Increment GLOBAL_CONFIG_VERSION
UPDATE pricing_configurations 
SET setting_value = (COALESCE(setting_value::bigint, 1) + 1)::text, 
    updated_at = CURRENT_TIMESTAMP 
WHERE setting_key = 'GLOBAL_CONFIG_VERSION' AND product_id IS NULL;
