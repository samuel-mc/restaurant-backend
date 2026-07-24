-- V8__ordering_enabled.sql
-- Permite desactivar pedidos desde el menú digital (consulta únicamente).

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS ordering_enabled BOOLEAN NOT NULL DEFAULT TRUE;
