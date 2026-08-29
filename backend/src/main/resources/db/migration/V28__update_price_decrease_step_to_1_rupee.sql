-- V28: Update Dynamic Pricing Engine Decrease Steps to Strictly ₹1.00

-- Update global pricing configuration settings in pricing_configurations
UPDATE pricing_configurations
SET setting_value = '1.00', updated_by = 'SYSTEM_V28_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'DECREASE_STEP_1' AND product_id IS NULL;

UPDATE pricing_configurations
SET setting_value = '1.00', updated_by = 'SYSTEM_V28_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'DECREASE_STEP_2' AND product_id IS NULL;

UPDATE pricing_configurations
SET setting_value = '1.00', updated_by = 'SYSTEM_V28_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'PRICE_DECREASE_STEP' AND product_id IS NULL;

-- Bump global config version
UPDATE pricing_configurations
SET setting_value = (COALESCE(setting_value::bigint, 1) + 1)::text, updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'GLOBAL_CONFIG_VERSION' AND product_id IS NULL;
