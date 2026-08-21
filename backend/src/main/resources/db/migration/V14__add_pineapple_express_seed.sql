-- Seed Pineapple Express if not present and create initial active 20L batch
INSERT INTO products (id, name, flavour, description, default_cup_size_ml, default_cup_price, current_cup_price, min_cup_price, max_cup_price)
VALUES (8, 'Pineapple Express', 'PINEAPPLE', 'Fresh tropical crushed pineapple nectar', 250, 20.00, 20.00, 18.00, 25.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO juice_batches (product_id, batch_code, container_capacity_ml, initial_volume_ml, remaining_volume_ml, cup_size_ml, status)
SELECT 8, 'BATCH-PNP-001', 20000, 20000, 20000, 250, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM juice_batches WHERE product_id = 8);

SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));
SELECT setval('juice_batches_id_seq', (SELECT MAX(id) FROM juice_batches));
