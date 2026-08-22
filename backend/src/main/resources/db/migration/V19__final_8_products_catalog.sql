-- V19: Final 8-Product Juice Exchange Market Catalog Migration

-- 1. Ensure is_active column exists on products table
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

-- 2. Deactivate any products that are not part of the final 8 catalog
UPDATE products SET is_active = FALSE WHERE flavour NOT IN ('MANGO', 'LEMON', 'ORANGE', 'MINT', 'GRAPE', 'STRAWBERRY', 'THUNDER', 'LYCHEE');

-- 3. Upsert the exact 8 final products with their baseline parameters and target sales
INSERT INTO products (name, flavour, description, default_cup_size_ml, default_cup_price, current_cup_price, min_cup_price, max_cup_price, target_sales_per_2_minute, is_active)
VALUES
('Fresh Mango Juice', 'MANGO', 'Sweet fresh Alphonso mango pulp juice', 250, 25.00, 25.00, 18.00, 35.00, 1.10, TRUE),
('Zesty Lemon Juice', 'LEMON', 'Refreshing squeezed lemonade with mint touch', 250, 25.00, 25.00, 18.00, 35.00, 0.80, TRUE),
('Valencia Orange Juice', 'ORANGE', 'Pure Valencia orange juice loaded with vitamin C', 250, 25.00, 25.00, 18.00, 35.00, 1.10, TRUE),
('Cool Mint Cooler', 'MINT', 'Chilled mint and lime mocktail blend', 250, 25.00, 25.00, 18.00, 35.00, 0.80, TRUE),
('Royal Grape Juice', 'GRAPE', 'Rich black grape extract cooler', 250, 25.00, 25.00, 18.00, 35.00, 0.90, TRUE),
('Strawberry Delight', 'STRAWBERRY', 'Fresh strawberry nectar crush', 250, 25.00, 25.00, 18.00, 35.00, 0.70, TRUE),
('Thunder', 'THUNDER', 'Electrifying energy fruit punch juice', 250, 25.00, 25.00, 18.00, 35.00, 0.90, TRUE),
('Lychee Mist', 'LYCHEE', 'Exotic lychee fruit punch', 250, 25.00, 25.00, 18.00, 35.00, 0.90, TRUE)
ON CONFLICT (flavour) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    default_cup_price = 25.00,
    current_cup_price = 25.00,
    min_cup_price = 18.00,
    max_cup_price = 35.00,
    target_sales_per_2_minute = EXCLUDED.target_sales_per_2_minute,
    is_active = TRUE;

-- 4. Ensure 20L initial batch for any active product without an active container
INSERT INTO juice_batches (product_id, batch_code, container_capacity_ml, initial_volume_ml, remaining_volume_ml, cup_size_ml, status)
SELECT p.id, 'BATCH-' || SUBSTRING(p.flavour, 1, 3) || '-001', 20000, 20000, 20000, 250, 'ACTIVE'
FROM products p
WHERE p.is_active = TRUE
  AND NOT EXISTS (SELECT 1 FROM juice_batches b WHERE b.product_id = p.id AND b.status = 'ACTIVE');
