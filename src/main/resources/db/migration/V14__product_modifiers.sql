-- V14: modificadores / extras de platillo con precio
-- Grupos (ej. Tamaño, Extras) + opciones con price_delta; snapshot en order_detail_modifiers.

CREATE TABLE product_modifier_groups (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    min_select INT NOT NULL DEFAULT 0,
    max_select INT NOT NULL DEFAULT 1,
    display_order INT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_modifier_group_select CHECK (min_select >= 0 AND max_select >= min_select)
);
CREATE INDEX idx_pmg_restaurant ON product_modifier_groups(restaurant_id);
CREATE INDEX idx_pmg_product ON product_modifier_groups(product_id);
CREATE INDEX idx_pmg_product_deleted ON product_modifier_groups(product_id, deleted);

CREATE TABLE product_modifiers (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES product_modifier_groups(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price_delta DECIMAL(10, 2) NOT NULL DEFAULT 0,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pm_restaurant ON product_modifiers(restaurant_id);
CREATE INDEX idx_pm_group ON product_modifiers(group_id);
CREATE INDEX idx_pm_group_deleted ON product_modifiers(group_id, deleted);

CREATE TABLE order_detail_modifiers (
    id BIGSERIAL PRIMARY KEY,
    order_detail_id BIGINT NOT NULL REFERENCES order_details(id) ON DELETE CASCADE,
    modifier_uuid UUID,
    name VARCHAR(100) NOT NULL,
    price_delta DECIMAL(10, 2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_odm_detail ON order_detail_modifiers(order_detail_id);
