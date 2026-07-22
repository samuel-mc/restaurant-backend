-- V5__subscription_plan.sql
-- Plan comercial BASIC | PRO (Enterprise reservado, no expuesto en registro)

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS plan VARCHAR(20) NOT NULL DEFAULT 'BASIC';

-- Demo italiano y tenants que ya tenían sitio publicado → Pro
UPDATE restaurants
SET plan = 'PRO'
WHERE LOWER(subdomain) IN ('latrattoria', 'la-trattoria')
   OR website_published = TRUE;
