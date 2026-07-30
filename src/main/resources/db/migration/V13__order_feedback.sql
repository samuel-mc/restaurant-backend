-- Feedback post-servicio (Smart Rating): una evaluación por pedido cerrado.
CREATE TABLE order_feedback (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    order_uuid      UUID NOT NULL,
    stars           SMALLINT NOT NULL,
    comment_text    VARCHAR(1000),
    contact         VARCHAR(120),
    reason          VARCHAR(40),
    outcome         VARCHAR(32) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    urgent          BOOLEAN NOT NULL DEFAULT FALSE,
    table_number    VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMPTZ,
    CONSTRAINT uq_order_feedback_order UNIQUE (order_id),
    CONSTRAINT chk_order_feedback_stars CHECK (stars BETWEEN 1 AND 5),
    CONSTRAINT chk_order_feedback_outcome CHECK (
        outcome IN ('GOOGLE_REVIEW', 'PRIVATE_COMPLAINT', 'THANKS')
    ),
    CONSTRAINT chk_order_feedback_status CHECK (
        status IN ('OPEN', 'RESOLVED', 'DISMISSED')
    )
);

CREATE INDEX idx_order_feedback_restaurant_created
    ON order_feedback (restaurant_id, created_at DESC);

CREATE INDEX idx_order_feedback_restaurant_urgent_open
    ON order_feedback (restaurant_id, urgent, status)
    WHERE urgent = TRUE AND status = 'OPEN';
