ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);

COMMENT ON COLUMN orders.payment_method IS
    'Método de cobro al cerrar: CASH | CARD | TRANSFER. Null en órdenes históricas o abiertas.';
