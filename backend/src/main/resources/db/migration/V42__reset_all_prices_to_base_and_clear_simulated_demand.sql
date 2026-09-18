-- V42: Reset all active drinks to base 25.00, clear order counts, and clear synthetic order counts
UPDATE products
SET current_cup_price = 25.00,
    default_cup_price = 25.00,
    min_cup_price = 20.00,
    max_cup_price = 30.00,
    weighted_sales = NULL,
    order_count = 0,
    last_price_change_timestamp = NULL,
    price_version = COALESCE(price_version, 0) + 1
WHERE is_active = TRUE;

-- Delete synthetic orders if any exist from test runs
DELETE FROM sales_order_items WHERE order_id IN (SELECT id FROM sales_orders WHERE payment_method = 'BOT_CASH');
DELETE FROM sales_orders WHERE payment_method = 'BOT_CASH';
