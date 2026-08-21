-- Align PostgreSQL primary key sequences after seeded inserts
SELECT setval('products_id_seq', COALESCE((SELECT MAX(id) FROM products), 1));
SELECT setval('juice_batches_id_seq', COALESCE((SELECT MAX(id) FROM juice_batches), 1));
