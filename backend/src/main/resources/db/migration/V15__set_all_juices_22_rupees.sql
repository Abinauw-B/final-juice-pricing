UPDATE products
SET default_cup_price = 22.00,
    current_cup_price = 22.00,
    min_cup_price = LEAST(min_cup_price, 18.00),
    max_cup_price = GREATEST(max_cup_price, 25.00);
