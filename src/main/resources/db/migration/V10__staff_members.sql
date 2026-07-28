-- Miembros del equipo operativo por restaurante (login rápido por PIN).
CREATE TABLE staff_members (
    id UUID PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    pin_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_staff_members_role CHECK (role IN ('ADMIN', 'MESERO', 'COCINA'))
);

CREATE INDEX idx_staff_members_restaurant_id ON staff_members (restaurant_id);
CREATE INDEX idx_staff_members_restaurant_active ON staff_members (restaurant_id, active);
