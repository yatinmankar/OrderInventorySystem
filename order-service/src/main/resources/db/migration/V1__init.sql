CREATE TABLE orders (
    id          VARCHAR(36)   PRIMARY KEY,
    product_id  VARCHAR(64)   NOT NULL,
    quantity    INT           NOT NULL,
    amount      NUMERIC(18,2) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Transaction outbox: written in the SAME tx as the order, drained to Kafka by the relay.
CREATE TABLE outbox (
    id            VARCHAR(36)  PRIMARY KEY,
    aggregate_id  VARCHAR(36)  NOT NULL,
    topic         VARCHAR(128) NOT NULL,
    type          VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    published     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at);

-- Saga state machine, keyed by orderId. This row is the orchestrator's idempotency guard.
CREATE TABLE saga_instance (
    order_id    VARCHAR(36)   PRIMARY KEY,
    state       VARCHAR(32)   NOT NULL,
    product_id  VARCHAR(64)   NOT NULL,
    quantity    INT           NOT NULL,
    amount      NUMERIC(18,2) NOT NULL,
    payment_id  VARCHAR(64),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
