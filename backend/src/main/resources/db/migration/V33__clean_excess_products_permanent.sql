-- V33: Permanently remove any excess or duplicate products, enforcing exactly the 8 canonical juices

DELETE FROM price_history WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM pricing_configurations WHERE product_id IS NOT NULL AND product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM sales_order_items WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM juice_batches WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM inventory_transactions WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM product_correlations WHERE source_product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23) OR target_product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM market_events WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
DELETE FROM market_crash_snapshots WHERE product_id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);

DELETE FROM products WHERE id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);

UPDATE products 
SET is_active = true
WHERE id IN (1, 2, 3, 4, 5, 6, 7, 23);
