-- V21: Add complete audit columns to price_history table
-- Enables full mathematical auditing of demand calculations per settlement

ALTER TABLE price_history ADD COLUMN IF NOT EXISTS raw_w0 INTEGER DEFAULT 0;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS raw_w1 INTEGER DEFAULT 0;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS raw_w2 INTEGER DEFAULT 0;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS unconsumed_w0 INTEGER DEFAULT 0;
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(50) DEFAULT 'SCHEDULED';
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS settlement_id VARCHAR(100);
