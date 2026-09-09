-- V39: Realistic Production Seed Data & State Normalization

-- 1. Ensure clean baseline attributes on the canonical 8 juices
UPDATE products
SET default_cup_price = 25.00,
    current_cup_price = 25.00,
    min_cup_price = 20.00,
    max_cup_price = 30.00,
    default_cup_size_ml = 250,
    pricing_mode = 'DYNAMIC',
    is_active = TRUE,
    weighted_sales = NULL,
    order_count = 0
WHERE id IN (1, 2, 3, 4, 5, 6, 7, 23);

-- 2. Ensure each canonical product has at least one active 20,000 ml batch
INSERT INTO juice_batches (product_id, batch_code, container_capacity_ml, initial_volume_ml, remaining_volume_ml, cup_size_ml, status, created_at, updated_at)
SELECT p.id,
       'BATCH-PROD-' || LPAD(p.id::text, 2, '0') || '-' || EXTRACT(EPOCH FROM NOW())::bigint,
       20000,
       20000,
       20000,
       250,
       'ACTIVE',
       NOW(),
       NOW()
FROM products p
WHERE p.id IN (1, 2, 3, 4, 5, 6, 7, 23)
  AND NOT EXISTS (
      SELECT 1 FROM juice_batches b
      WHERE b.product_id = p.id AND b.status = 'ACTIVE' AND b.remaining_volume_ml >= 5000
  );

-- 3. Seed initial system notification
INSERT INTO system_notifications (title, message, type, is_read, created_at)
VALUES (
    'Juice Exchange Online',
    'Platform initialized. All 8 beverage stock counters active at base price ₹25.00 with full 20L containers.',
    'INFO',
    FALSE,
    NOW()
);

-- 4. Seed initial audit log entry
INSERT INTO audit_logs (user_id, action, module, details, ip_address, created_at)
VALUES (
    1,
    'SYSTEM_BOOT',
    'SYSTEM',
    'Realistic production seed migration V39 executed successfully.',
    '127.0.0.1',
    NOW()
);
