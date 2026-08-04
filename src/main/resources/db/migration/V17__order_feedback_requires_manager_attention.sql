ALTER TABLE order_feedback
    ADD COLUMN requires_manager_attention BOOLEAN NOT NULL DEFAULT FALSE;
