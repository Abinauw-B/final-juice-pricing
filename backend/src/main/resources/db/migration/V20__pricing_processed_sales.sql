-- V20: Pricing Processed Sales Tracking
-- Prevents repeated pricing adjustments from the same sales order items

CREATE TABLE IF NOT EXISTS pricing_processed_sales (
    id BIGSERIAL PRIMARY KEY,
    sale_item_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    settlement_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_sales_item ON pricing_processed_sales(sale_item_id);
CREATE INDEX IF NOT EXISTS idx_processed_sales_prod ON pricing_processed_sales(product_id);
CREATE INDEX IF NOT EXISTS idx_processed_sales_settlement ON pricing_processed_sales(settlement_id);
