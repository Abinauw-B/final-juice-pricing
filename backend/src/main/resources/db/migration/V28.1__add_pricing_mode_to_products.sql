-- V28.1: Add pricing_mode column required by V29+ migrations and Product entity (ddl-auto: validate)
-- Tracks whether dynamic pricing or manual lock is active per product.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(30) NOT NULL DEFAULT 'DYNAMIC';

UPDATE products
SET pricing_mode = 'DYNAMIC'
WHERE pricing_mode IS NULL;
