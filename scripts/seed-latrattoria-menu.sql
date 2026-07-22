-- Seed demo: menú italiano para tenant latrattoria (restaurant_id = 2)
-- Idempotente a nivel de nombres: no duplica productos ya existentes con el mismo nombre.

BEGIN;

-- Soft-delete productos demo poco italianos
UPDATE products
SET deleted = true
WHERE restaurant_id = 2
  AND deleted = false
  AND name IN ('Papas a la francesa', 'Sprite');

-- Categorías (crear si faltan; actualizar orden/nombres)
INSERT INTO categories (restaurant_id, name, display_order, deleted)
SELECT 2, v.name, v.display_order, false
FROM (VALUES
    ('Entradas', 1),
    ('Pastas', 2),
    ('Pizzas', 3),
    ('Platos fuertes', 4),
    ('Postres', 5),
    ('Bebidas', 6)
) AS v(name, display_order)
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.restaurant_id = 2
      AND c.deleted = false
      AND lower(c.name) = lower(v.name)
);

UPDATE categories SET display_order = 1, name = 'Entradas'
WHERE restaurant_id = 2 AND deleted = false AND lower(name) IN ('entradas', 'antipasti');
UPDATE categories SET display_order = 2, name = 'Pastas'
WHERE restaurant_id = 2 AND deleted = false AND lower(name) = 'pastas';
UPDATE categories SET display_order = 3, name = 'Pizzas'
WHERE restaurant_id = 2 AND deleted = false AND lower(name) = 'pizzas';
UPDATE categories SET display_order = 4, name = 'Platos fuertes'
WHERE restaurant_id = 2 AND deleted = false AND lower(name) IN ('platos fuertes', 'secondi');
UPDATE categories SET display_order = 5, name = 'Postres'
WHERE restaurant_id = 2 AND deleted = false AND lower(name) IN ('postres', 'dolci');
UPDATE categories SET display_order = 6, name = 'Bebidas'
WHERE restaurant_id = 2 AND deleted = false AND lower(name) IN ('bebidas', 'bevande');

-- Helper: insert product if name not already active for this restaurant
WITH catalog (category_name, name, description, price) AS (
    VALUES
    -- Entradas (6)
    ('Entradas', 'Bruschetta al Pomodoro', 'Pan tostado con tomate fresco, albahaca y aceite de oliva.', 95.00),
    ('Entradas', 'Insalata Caprese', 'Mozzarella fresca, tomate y albahaca con reducción balsámica.', 120.00),
    ('Entradas', 'Antipasto Misto', 'Selección de embutidos, quesos italianos y aceitunas.', 180.00),
    ('Entradas', 'Carpaccio di Manzo', 'Filete fino de res, rúcula, parmesano y limón.', 195.00),
    ('Entradas', 'Arancini Siciliani', 'Croquetas de risotto rellenas de ragú y mozzarella.', 110.00),
    ('Entradas', 'Insalata Cesare', 'Lechuga romana, crutones, parmesano y aderezo César.', 130.00),

    -- Pastas (8)
    ('Pastas', 'Spaghetti Carbonara', 'Guanciale, huevo, pecorino romano y pimienta negra.', 185.00),
    ('Pastas', 'Tagliatelle al Ragù', 'Pasta fresca con ragú boloñés de la casa.', 175.00),
    ('Pastas', 'Penne all''Arrabbiata', 'Salsa de tomate picante, ajo y chile.', 155.00),
    ('Pastas', 'Fettuccine Alfredo', 'Salsa cremosa de parmesano y mantequilla.', 170.00),
    ('Pastas', 'Linguine alle Vongole', 'Almejas, ajo, vino blanco y perejil.', 210.00),
    ('Pastas', 'Lasagna della Casa', 'Capas de pasta, ragú, bechamel y queso gratinado.', 195.00),
    ('Pastas', 'Risotto ai Funghi', 'Arroz arbóreo con hongos silvestres y parmesano.', 190.00),
    ('Pastas', 'Gnocchi al Pesto', 'Ñoquis de papa con pesto genovés y piñones.', 165.00),

    -- Pizzas (6) — Pepperoni ya existe
    ('Pizzas', 'Pizza Margherita', 'Salsa de tomate San Marzano, mozzarella y albahaca.', 195.00),
    ('Pizzas', 'Pizza Quattro Formaggi', 'Mozzarella, gorgonzola, parmesano y provolone.', 230.00),
    ('Pizzas', 'Pizza Diavola', 'Salami picante, mozzarella y chile.', 220.00),
    ('Pizzas', 'Pizza Prosciutto e Funghi', 'Jamón crudo, champiñones y mozzarella.', 240.00),
    ('Pizzas', 'Pizza Ortolana', 'Verduras de temporada asadas y mozzarella.', 210.00),
    ('Pizzas', 'Calzone Classico', 'Calzone relleno de ricotta, mozzarella y jamón.', 225.00),

    -- Platos fuertes (4)
    ('Platos fuertes', 'Pollo alla Parmigiana', 'Pechuga empanizada, salsa de tomate y mozzarella gratinada.', 240.00),
    ('Platos fuertes', 'Salmone alla Griglia', 'Salmón a la parrilla con limón y hierbas.', 280.00),
    ('Platos fuertes', 'Ossobuco alla Milanese', 'Jarreté de ternera estofado con gremolata.', 320.00),
    ('Platos fuertes', 'Scaloppine al Limone', 'Escalopes de ternera al limón y mantequilla.', 260.00),

    -- Postres (4)
    ('Postres', 'Tiramisú', 'Clásico con café espresso, mascarpone y cacao.', 95.00),
    ('Postres', 'Panna Cotta', 'Crema de vainilla con coulis de frutos rojos.', 85.00),
    ('Postres', 'Cannoli Siciliani', 'Canutos crujientes rellenos de ricotta dulce.', 90.00),
    ('Postres', 'Gelato Artesanal', 'Dos bolas de helado italiano de la casa.', 75.00),

    -- Bebidas (5) — Coca cola ya existe
    ('Bebidas', 'Acqua Panna', 'Agua natural italiana 500 ml.', 45.00),
    ('Bebidas', 'San Pellegrino', 'Agua mineral con gas 500 ml.', 50.00),
    ('Bebidas', 'Espresso', 'Café espresso italiano.', 45.00),
    ('Bebidas', 'Cappuccino', 'Espresso con leche vaporizada y espuma.', 65.00),
    ('Bebidas', 'Copa de Chianti', 'Vino tinto Chianti Classico (copa).', 120.00),
    ('Bebidas', 'Limoncello', 'Licor de limón de Amalfi (shot).', 85.00)
)
INSERT INTO products (
    restaurant_id,
    category_id,
    name,
    description,
    price,
    is_available,
    deleted,
    uuid
)
SELECT
    2,
    c.id,
    catalog.name,
    catalog.description,
    catalog.price::numeric(10,2),
    true,
    false,
    gen_random_uuid()
FROM catalog
JOIN categories c
  ON c.restaurant_id = 2
 AND c.deleted = false
 AND lower(c.name) = lower(catalog.category_name)
WHERE NOT EXISTS (
    SELECT 1 FROM products p
    WHERE p.restaurant_id = 2
      AND p.deleted = false
      AND lower(p.name) = lower(catalog.name)
);

-- Afinar descripción del pepperoni existente si sigue activo
UPDATE products
SET description = COALESCE(description, 'Pepperoni, mozzarella y salsa de tomate.')
WHERE restaurant_id = 2
  AND deleted = false
  AND name = 'Pizza de pepperoni'
  AND (description IS NULL OR description = '');

COMMIT;

-- Resumen
SELECT c.display_order, c.name, count(p.id) AS productos
FROM categories c
LEFT JOIN products p
  ON p.category_id = c.id
 AND p.restaurant_id = 2
 AND p.deleted = false
WHERE c.restaurant_id = 2
  AND c.deleted = false
GROUP BY c.id, c.display_order, c.name
ORDER BY c.display_order, c.name;

SELECT count(*) AS total_productos_activos
FROM products
WHERE restaurant_id = 2 AND deleted = false;
