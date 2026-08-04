-- ==============================================================================
-- MIGRACIÓN DE DATOS DE PRUEBA Y ENTORNO QA (PLATOLISTO)
-- Tenant: latrattoria (La Trattoria Italiana)
-- ==============================================================================

-- 1. Insertar Restaurante / Tenant QA (si no existe)
INSERT INTO restaurants (name, subdomain, table_count, ordering_enabled, is_active, plan, created_at, updated_at)
SELECT 'La Trattoria Italiana', 'latrattoria', 4, true, true, 'PRO', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM restaurants WHERE subdomain = 'latrattoria');

-- 2. Insertar Categorías de Prueba
INSERT INTO categories (restaurant_id, name, created_at)
SELECT r.id, c.name, NOW()
FROM restaurants r
CROSS JOIN (VALUES
    ('Pizzas'),
    ('Pastas'),
    ('Bebidas')
) AS c(name)
WHERE r.subdomain = 'latrattoria'
  AND NOT EXISTS (SELECT 1 FROM categories cat WHERE cat.restaurant_id = r.id AND cat.name = c.name);

-- 3. Insertar Productos de Prueba
INSERT INTO products (restaurant_id, category_id, uuid, name, description, price, image_url, is_available, created_at)
SELECT
    r.id,
    cat.id,
    p.uuid::uuid,
    p.name,
    p.description,
    p.price,
    p.image_url,
    true,
    NOW()
FROM restaurants r
JOIN categories cat ON cat.restaurant_id = r.id
CROSS JOIN (VALUES
    ('Pizzas', 'e0111111-1111-4111-a111-111111111111', 'Pizza Margherita', 'Mozzarella fresca, tomate y albahaca', 180.00, 'https://images.unsplash.com/photo-1604382354936-07c5d9983bd3'),
    ('Pizzas', 'e0222222-2222-4222-a222-222222222222', 'Pizza Pepperoni', 'Pepperoni artesanal y queso fundido', 210.00, 'https://images.unsplash.com/photo-1628840042765-356cda07504e'),
    ('Pastas', 'e0333333-3333-4333-a333-333333333333', 'Fettuccine Alfredo', 'Salsa cremosa de parmesano y mantequilla', 195.00, 'https://images.unsplash.com/photo-1645112411341-6c4fd023714a'),
    ('Pastas', 'e0444444-4444-4444-a444-444444444444', 'Lasagna Bolognese', 'Capas de pasta con carne magra y bechamel', 220.00, 'https://images.unsplash.com/photo-1574894709920-11b28e7367e3'),
    ('Bebidas', 'e0555555-5555-4555-a555-555555555555', 'Agua de Limón con Menta', 'Fresca e infusionada al instante (1L)', 45.00, 'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd')
) AS p(category_name, uuid, name, description, price, image_url)
WHERE r.subdomain = 'latrattoria' AND cat.name = p.category_name
  AND NOT EXISTS (SELECT 1 FROM products prod WHERE prod.restaurant_id = r.id AND prod.name = p.name);

-- 4. Insertar Grupo de Modificadores ("Opciones Extra") para Pizzas y Pastas
INSERT INTO product_modifier_groups (restaurant_id, product_id, uuid, name, min_select, max_select, display_order, deleted, created_at)
SELECT r.id, p.id, gen_random_uuid(), 'Opciones Extra', 0, 3, 1, false, NOW()
FROM restaurants r
JOIN products p ON p.restaurant_id = r.id
WHERE r.subdomain = 'latrattoria' AND p.name IN ('Pizza Margherita', 'Pizza Pepperoni', 'Fettuccine Alfredo', 'Lasagna Bolognese')
  AND NOT EXISTS (SELECT 1 FROM product_modifier_groups pmg WHERE pmg.product_id = p.id AND pmg.name = 'Opciones Extra');

-- Insertar Opciones del Modificador ("Extra Queso Mozzarella", "Salsa Especial de la Casa")
INSERT INTO product_modifiers (restaurant_id, group_id, uuid, name, price_delta, is_available, display_order, deleted, created_at)
SELECT r.id, pmg.id, gen_random_uuid(), m.name, m.price_delta, true, 1, false, NOW()
FROM restaurants r
JOIN product_modifier_groups pmg ON pmg.restaurant_id = r.id
CROSS JOIN (VALUES
    ('Extra Queso Mozzarella', 25.00),
    ('Salsa Especial de la Casa', 15.00)
) AS m(name, price_delta)
WHERE r.subdomain = 'latrattoria' AND pmg.name = 'Opciones Extra'
  AND NOT EXISTS (SELECT 1 FROM product_modifiers pm WHERE pm.group_id = pmg.id AND pm.name = m.name);

-- 5. Insertar Personal de Staff QA con PINs Preconfigurados
-- PIN Admin: 1111 | PIN Mesero: 2222 | PIN Cocina: 3333
INSERT INTO staff_members (id, restaurant_id, name, role, pin_hash, active, created_at, updated_at)
SELECT
    s.id::uuid,
    r.id,
    s.name,
    s.role,
    s.pin_hash,
    true,
    NOW(),
    NOW()
FROM restaurants r
CROSS JOIN (VALUES
    ('d0111111-1111-4111-c111-111111111111', 'Admin QA', 'ADMIN', '$2a$10$wT8KzV4C1s0H2f0N4eE2Y8O8A8xG6.kZ2J9W2F0n4eE2Y8O8A8xG6.'),
    ('d0222222-2222-4222-c222-222222222222', 'Mesero Carlos', 'MESERO', '$2a$10$xS.1N2kX34z567890abcdefg.e222222222222222222222222222'),
    ('d0333333-3333-4333-c333-333333333333', 'Chef Luigi', 'COCINA', '$2a$10$yT.1N2kX34z567890abcdefg.e333333333333333333333333333')
) AS s(id, name, role, pin_hash)
WHERE r.subdomain = 'latrattoria'
  AND NOT EXISTS (SELECT 1 FROM staff_members sm WHERE sm.restaurant_id = r.id AND sm.name = s.name);
