-- V35: Remove/deactivate obsolete -2 movement configuration and enforce strictly 1.00 decrease step
-- This migration ensures allowed decrease step is 1.00 and preserves individual product pricing configurations.

UPDATE pricing_configurations
SET setting_value = '1.00', updated_by = 'SYSTEM_V35_MIGRATION', updated_at = CURRENT_TIMESTAMP
WHERE setting_key IN ('DECREASE_STEP_1', 'DECREASE_STEP_2', 'PRICE_DECREASE_STEP') AND product_id IS NULL;

-- Bump global config version
UPDATE pricing_configurations
SET setting_value = (COALESCE(setting_value::bigint, 1) + 1)::text, updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'GLOBAL_CONFIG_VERSION' AND product_id IS NULL;
