-- V2__add_uuid_to_products.sql
-- Agregar columna uuid autogenerada a la tabla products

ALTER TABLE products ADD COLUMN uuid UUID DEFAULT gen_random_uuid() NOT NULL;

-- Asegurar que la columna tenga restricción de unicidad e índice
ALTER TABLE products ADD CONSTRAINT uk_products_uuid UNIQUE (uuid);
CREATE INDEX idx_products_uuid ON products(uuid);
