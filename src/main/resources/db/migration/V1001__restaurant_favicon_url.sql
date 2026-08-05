-- Favicon dedicado por tenant (pestaña del navegador / apple-touch).
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS favicon_url VARCHAR(512);
