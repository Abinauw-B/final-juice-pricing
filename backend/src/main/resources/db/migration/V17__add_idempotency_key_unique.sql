ALTER TABLE sales_orders ADD CONSTRAINT uk_sales_orders_idempotency_key UNIQUE (idempotency_key);
