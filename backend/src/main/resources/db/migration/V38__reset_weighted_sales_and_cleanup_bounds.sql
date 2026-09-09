-- V38: Reset weighted_sales default to NULL so DWMA reflects live sales demand naturally
ALTER TABLE products ALTER COLUMN weighted_sales DROP DEFAULT;
UPDATE products SET weighted_sales = NULL;

-- Ensure canonical product 1 (Fresh Mango Juice) has clean baseline bounds
UPDATE products 
SET min_cup_price = 20.00,
    max_cup_price = 30.00,
    default_cup_price = 25.00,
    current_cup_price = 25.00,
    target_sales_per_1_minute = 0.55,
    pricing_mode = 'DYNAMIC',
    weighted_sales = NULL
WHERE id = 1;
