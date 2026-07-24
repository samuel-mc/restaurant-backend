-- V9__order_detail_rounds.sql
-- Rondas de pedido (batch) + estado por ítem

ALTER TABLE order_details
    ADD COLUMN IF NOT EXISTS batch_number INTEGER NOT NULL DEFAULT 1;

ALTER TABLE order_details
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

COMMENT ON COLUMN order_details.batch_number IS 'Ronda del pedido: 1 inicial, 2+ adiciones';
COMMENT ON COLUMN order_details.status IS 'PENDING | PREPARING | DELIVERED';
