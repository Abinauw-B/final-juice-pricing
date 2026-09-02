-- V32: Keep only the 8 canonical beverages and remove newly added / duplicate flavours

-- 1. Remove child records referencing non-canonical products
DELETE FROM price_history WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM pricing_configurations WHERE product_id IS NOT NULL AND product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM sales_order_items WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM juice_batches WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM inventory_transactions WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM product_correlations WHERE source_product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23) OR target_product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM market_events WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM market_crash_snapshots WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);

-- 2. Delete non-canonical products from products table
DELETE FROM products WHERE id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);

-- 3. Ensure the exact 8 canonical products exist with proper attributes
UPDATE products 
SET is_active = true,
    default_cup_price = 25.00,
    current_cup_price = 25.00,
    min_cup_price = 20.00,
    max_cup_price = 30.00,
    target_sales_per_1_minute = COALESCE(target_sales_per_1_minute, 0.55),
    pricing_mode = 'DYNAMIC'
WHERE id IN (1, 2, 3, 4, 5, 6, 7, 23);
