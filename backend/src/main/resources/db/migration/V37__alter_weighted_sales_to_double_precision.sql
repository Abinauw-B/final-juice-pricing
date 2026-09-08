-- V37: Alter weighted_sales to DOUBLE PRECISION to match JPA Double entity mapping
ALTER TABLE products ALTER COLUMN weighted_sales TYPE DOUBLE PRECISION USING weighted_sales::double precision;
