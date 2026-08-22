-- V22__add_volatility_round_pricing_fields.sql
-- Add Volatility Round Pricing fields to products and price_history tables

-- 1. Add order_count, target_orders, volatility to products table
ALTER TABLE products
ADD COLUMN IF NOT EXISTS order_count INT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS target_orders INT NOT NULL DEFAULT 5,
ADD COLUMN IF NOT EXISTS volatility NUMERIC(5, 4) NOT NULL DEFAULT 0.0800;

-- Initialize active products with authoritative defaults
UPDATE products
SET order_count = 0,
    target_orders = 5,
    volatility = 0.0800;

-- 2. Add round pricing audit columns to price_history table
ALTER TABLE price_history
ADD COLUMN IF NOT EXISTS order_count INT DEFAULT 0,
ADD COLUMN IF NOT EXISTS raw_price_change_percent NUMERIC(8, 4) DEFAULT 0.0000,
ADD COLUMN IF NOT EXISTS applied_price_change_percent NUMERIC(8, 4) DEFAULT 0.0000,
ADD COLUMN IF NOT EXISTS volatility NUMERIC(5, 4) DEFAULT 0.0800,
ADD COLUMN IF NOT EXISTS floor_price NUMERIC(10, 2),
ADD COLUMN IF NOT EXISTS ceiling_price NUMERIC(10, 2),
ADD COLUMN IF NOT EXISTS price_version INT DEFAULT 1;
