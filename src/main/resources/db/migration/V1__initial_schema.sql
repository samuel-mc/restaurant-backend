-- V1__initial_schema.sql
-- Creación inicial del esquema de base de datos consolidado

-- Habilitar extensión para UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Tabla: restaurants (El Tenant Core)
CREATE TABLE restaurants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    subdomain VARCHAR(50) NOT NULL UNIQUE, -- Ej: 'burgers', 'pizzeria'
    custom_domain VARCHAR(100) UNIQUE,     -- Opcional (Ej: 'pizzeriamario.com')
    logo_url VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_restaurants_subdomain ON restaurants(subdomain);
CREATE INDEX idx_restaurants_custom_domain ON restaurants(custom_domain);

-- 2. Tabla: users (Administradores y Empleados)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL, -- Para Spring Security (BCrypt)
    role VARCHAR(20) NOT NULL, -- 'OWNER', 'ADMIN', 'CASHIER', 'KITCHEN'
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_restaurant_user_email UNIQUE (restaurant_id, email) -- Email único por restaurante
);
CREATE INDEX idx_users_restaurant ON users(restaurant_id);

-- 3. Tabla: categories (Categorías del Menú)
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    display_order INT DEFAULT 0 NOT NULL, -- Para ordenar el menú en Next.js
    deleted BOOLEAN DEFAULT FALSE NOT NULL, -- Regla 6: Soft Delete
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_categories_restaurant_id ON categories(restaurant_id);
CREATE INDEX idx_categories_restaurant_deleted ON categories(restaurant_id, deleted);

-- 4. Tabla: products (Platillos / Productos)
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL, -- Regla 4: Finanzas (BigDecimal/Decimal(10,2))
    image_url VARCHAR(255),
    is_available BOOLEAN DEFAULT TRUE NOT NULL, -- Switch rápido de stock
    deleted BOOLEAN DEFAULT FALSE NOT NULL, -- Regla 6: Soft Delete
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_products_restaurant ON products(restaurant_id);
CREATE INDEX idx_products_restaurant_deleted ON products(restaurant_id, deleted);

-- 5. Tabla: orders (Cabecera de Pedidos)
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY, -- ID secuencial interno
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE, -- Regla 5: UUID público
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20),
    order_type VARCHAR(20) NOT NULL, -- 'IN_TABLE', 'PICKUP', 'DELIVERY'
    table_number VARCHAR(10), -- Opcional, solo si order_type = 'IN_TABLE'
    delivery_address TEXT,    -- Opcional, solo si order_type = 'DELIVERY'
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL, -- 'PENDING', 'ACCEPTED', 'IN_KITCHEN', 'DELIVERED', 'CANCELLED'
    total_amount DECIMAL(10, 2) NOT NULL, -- Regla 4: DECIMAL(10,2)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_orders_restaurant_status ON orders(restaurant_id, status);
CREATE INDEX idx_orders_uuid ON orders(uuid);

-- 6. Tabla: order_details (Detalle de Pedidos)
CREATE TABLE order_details (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10, 2) NOT NULL, -- Precio al momento de la compra
    notes VARCHAR(255) -- Ej: "Sin cebolla"
);
CREATE INDEX idx_order_details_order_id ON order_details(order_id);
