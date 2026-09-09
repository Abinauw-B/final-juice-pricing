-- V40: Performance Tuning & Batch Query Optimization Indexes

-- 1. Index on juice_batches for ultra-fast pessimistic lock acquisition during checkout
CREATE INDEX IF NOT EXISTS idx_juice_batches_product_status
ON juice_batches (product_id, status);

-- 2. Foreign key index on sales_order_items (order_id) to eliminate table scans during join fetches
CREATE INDEX IF NOT EXISTS idx_sales_order_items_order_id
ON sales_order_items (order_id);

-- 3. Composite index on audit_logs for module/action filtering
CREATE INDEX IF NOT EXISTS idx_audit_logs_module_action_created
ON audit_logs (module, action, created_at DESC);
