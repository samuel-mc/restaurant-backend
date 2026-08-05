-- V1002__subscription_billing_period.sql
-- Período de suscripción en el tenant + duración de grant del cupón (independiente de expires_at).

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS current_period_start TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS current_period_end   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS billing_interval     VARCHAR(20);

COMMENT ON COLUMN restaurants.current_period_start IS
    'Inicio del período de suscripción pagado / otorgado.';
COMMENT ON COLUMN restaurants.current_period_end IS
    'Fecha en la que toca renovar (fuente de verdad de renovación).';
COMMENT ON COLUMN restaurants.billing_interval IS
    'Intervalo comercial: MONTHLY | YEARLY (opcional).';

CREATE INDEX IF NOT EXISTS idx_restaurants_current_period_end
    ON restaurants (current_period_end)
    WHERE current_period_end IS NOT NULL;

ALTER TABLE coupons
    ADD COLUMN IF NOT EXISTS grant_duration_days INTEGER;

COMMENT ON COLUMN coupons.grant_duration_days IS
    'Días de entitlement al canjear. Null = no fija período. Distinto de expires_at (ventana de canje).';

-- Cupones demo locales: período mensual al canjear (sin cambiar expires_at).
UPDATE coupons
SET grant_duration_days = 30
WHERE code IN ('PRO-DEMO-2026', 'SETUP-CASH-1000')
  AND grant_duration_days IS NULL;
