-- V7__super_admin_support.sql
-- Usuarios plataforma (SUPER_ADMIN) sin restaurant_id obligatorio

ALTER TABLE users
    ALTER COLUMN restaurant_id DROP NOT NULL;

ALTER TABLE users
    ALTER COLUMN role TYPE VARCHAR(30);

-- Índice para login global de superadmin
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_superadmin_email
    ON users (lower(email))
    WHERE role = 'SUPER_ADMIN';
