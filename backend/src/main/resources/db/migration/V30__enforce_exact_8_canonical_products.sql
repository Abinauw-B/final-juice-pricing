-- V30: Enforce Exact 8 Canonical Products and Deactivate Duplicate Rows

-- 1. Deactivate duplicate product rows
UPDATE products
SET is_active = FALSE
WHERE id IN (25, 37, 38)
   OR flavour IN ('MANGO', 'ORANGE', 'MINT');

-- 2. Ensure the exact 8 canonical products are active with standard metadata
UPDATE products
SET is_active = TRUE
WHERE id IN (1, 2, 3, 4, 5, 6, 7, 23);

-- 3. Deactivate any other unexpected products
UPDATE products
SET is_active = FALSE
WHERE id NOT IN (1, 2, 3, 4, 5, 6, 7, 23);
