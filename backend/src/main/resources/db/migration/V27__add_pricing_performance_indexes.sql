-- V27: Add Composite Performance Indexes for DWMA Pricing Time-Window Aggregations

-- 1. Index on sales_orders for high-speed time window range scans
CREATE INDEX IF NOT EXISTS idx_sales_orders_created_at_id 
ON sales_orders (created_at, id);

-- 2. Covering composite index on sales_order_items for index-only scans during window count
CREATE INDEX IF NOT EXISTS idx_sales_order_items_product_order_qty 
ON sales_order_items (product_id, order_id, quantity);

-- 3. Composite index on price_history for fast product history retrieval
CREATE INDEX IF NOT EXISTS idx_price_history_product_created 
ON price_history (product_id, created_at DESC);
