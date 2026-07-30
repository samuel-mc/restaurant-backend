-- Cantidad de mesas del salón (configurable por restaurante).
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS table_count INTEGER NOT NULL DEFAULT 12;

ALTER TABLE restaurants
    DROP CONSTRAINT IF EXISTS chk_restaurants_table_count;

ALTER TABLE restaurants
    ADD CONSTRAINT chk_restaurants_table_count
        CHECK (table_count >= 1 AND table_count <= 99);
