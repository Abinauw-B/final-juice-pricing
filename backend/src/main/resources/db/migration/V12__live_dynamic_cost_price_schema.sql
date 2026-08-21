-- Migration V12: Live Dynamic Cost & Price System Schema Extensions

-- Add price_version to products
ALTER TABLE products ADD COLUMN IF NOT EXISTS price_version INT NOT NULL DEFAULT 1;

-- Add idempotency_key, subtotal, discount_amount, tax_amount to sales_orders
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS subtotal DECIMAL(10, 2) DEFAULT 0.00;
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(10, 2) DEFAULT 0.00;
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(10, 2) DEFAULT 0.00;

-- Add locked_price, price_version, created_at to sales_order_items
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS locked_price DECIMAL(10, 2) NOT NULL DEFAULT 20.00;
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS price_version INT NOT NULL DEFAULT 1;
ALTER TABLE sales_order_items ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
