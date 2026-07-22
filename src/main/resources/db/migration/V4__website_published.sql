-- V4__website_published.sql
-- Publicación del website institucional por tenant (sin registro hardcodeado en frontend)

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS website_published BOOLEAN NOT NULL DEFAULT FALSE;

-- Preservar el sitio ya publicado en producción / demo
UPDATE restaurants
SET website_published = TRUE
WHERE LOWER(subdomain) = 'latrattoria';
