-- Secreto por restaurante para firmar tokens de QR de mesa (HMAC).
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS table_qr_secret VARCHAR(64);

COMMENT ON COLUMN restaurants.table_qr_secret IS
    'Secreto Base64 (32 bytes) para firmar ?t= en QR de mesa. Se genera al primer uso.';
