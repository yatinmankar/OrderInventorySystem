-- Hibernate Envers history for saga_instance. One row per committed transaction
-- that touched an @Audited entity (order-service has just SagaInstance today).
CREATE TABLE revinfo (
    rev      BIGSERIAL PRIMARY KEY,
    revtstmp BIGINT NOT NULL
);

-- Shadow table for SagaInstance: one row per revision, holding the entity's
-- column values as of that revision plus revtype (0=ADD, 1=MOD, 2=DEL).
CREATE TABLE saga_instance_aud (
    order_id    VARCHAR(36)   NOT NULL,
    rev         BIGINT        NOT NULL REFERENCES revinfo (rev),
    revtype     SMALLINT,
    state       VARCHAR(32),
    product_id  VARCHAR(64),
    quantity    INT,
    amount      NUMERIC(18,2),
    payment_id  VARCHAR(64),
    created_at  TIMESTAMP WITH TIME ZONE,
    updated_at  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (order_id, rev)
);
CREATE INDEX idx_saga_instance_aud_order_id ON saga_instance_aud (order_id);
