-- Payment keyed by order_id => natural idempotency key.
CREATE TABLE payment (
    order_id   VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(64),
    amount     NUMERIC(18,2) NOT NULL,
    status     VARCHAR(16) NOT NULL,  -- COMPLETED | DECLINED | REFUNDED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
