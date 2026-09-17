-- V41: Add crash sale tracking, settlement cycle tracking, and demand performance index

-- 1. Add is_crash_sale to sales_orders
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS is_crash_sale BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Add is_crash_sale to sales_order_items
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS is_crash_sale BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Add cycle_id to price_history for complete settlement traceability
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS cycle_id VARCHAR(100);

-- 4. High-performance composite index for demand window filtering (excluding crash sales)
CREATE INDEX IF NOT EXISTS idx_sales_items_demand_calc
ON sales_order_items (product_id, is_crash_sale, created_at);
