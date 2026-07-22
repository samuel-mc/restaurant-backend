-- V6__payment_status_and_coupons.sql
-- Estado de pago + cupones para activar Pro (efectivo / transferencia / early access)

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

-- Demos Pro ya operativos quedan ACTIVE; no forzar PENDING en históricos
UPDATE restaurants
SET payment_status = 'ACTIVE'
WHERE plan = 'PRO';

CREATE TABLE IF NOT EXISTS coupons (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(40)  NOT NULL UNIQUE,
    description       VARCHAR(255),
    grants_plan       VARCHAR(20)  NOT NULL DEFAULT 'PRO',
    max_redemptions   INTEGER,
    redemption_count  INTEGER      NOT NULL DEFAULT 0,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id             BIGSERIAL PRIMARY KEY,
    coupon_id      BIGINT       NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    redeemed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_coupon_restaurant UNIQUE (coupon_id, restaurant_id)
);

CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_restaurant
    ON coupon_redemptions (restaurant_id);

-- Cupones demo locales (efectivo / early access)
INSERT INTO coupons (code, description, grants_plan, max_redemptions, active)
VALUES
    ('PRO-DEMO-2026', 'Activación Pro early access / demo', 'PRO', NULL, TRUE),
    ('SETUP-CASH-1000', 'Pago setup $1,000 MXN en efectivo confirmado', 'PRO', NULL, TRUE)
ON CONFLICT (code) DO NOTHING;
