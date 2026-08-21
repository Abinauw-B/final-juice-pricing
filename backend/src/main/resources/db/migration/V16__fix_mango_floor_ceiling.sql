UPDATE products
SET min_cup_price = 18.00,
    max_cup_price = 25.00
WHERE id = 1 AND (min_cup_price = 22.00 OR max_cup_price = 22.00);
