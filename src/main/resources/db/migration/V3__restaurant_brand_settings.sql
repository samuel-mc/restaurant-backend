-- V3__restaurant_brand_settings.sql
-- Identidad de marca, info comercial y módulos SaaS

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS banner_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS primary_color VARCHAR(7),
    ADD COLUMN IF NOT EXISTS secondary_color VARCHAR(7),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS google_maps_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS whatsapp VARCHAR(30),
    ADD COLUMN IF NOT EXISTS business_hours VARCHAR(255),
    ADD COLUMN IF NOT EXISTS has_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_pickup BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS has_reservations BOOLEAN NOT NULL DEFAULT FALSE;

-- Ampliar logo_url por si R2 entrega URLs largas
ALTER TABLE restaurants
    ALTER COLUMN logo_url TYPE VARCHAR(512);
