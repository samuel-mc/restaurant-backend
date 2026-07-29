-- Defaults para tenants nuevos: módulos operativos desactivados.
-- No modifica filas existentes; solo el DEFAULT de columna en inserts futuros.

ALTER TABLE restaurants
    ALTER COLUMN has_pickup SET DEFAULT FALSE;

ALTER TABLE restaurants
    ALTER COLUMN ordering_enabled SET DEFAULT FALSE;
