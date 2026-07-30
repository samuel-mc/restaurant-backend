-- Mesas vinculadas (unión de cuentas) y atribución del mesero en comandas.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS linked_tables VARCHAR(255),
    ADD COLUMN IF NOT EXISTS staff_id UUID,
    ADD COLUMN IF NOT EXISTS staff_name VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_orders_staff_id ON orders (staff_id)
    WHERE staff_id IS NOT NULL;
