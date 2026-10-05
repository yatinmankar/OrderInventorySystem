-- Shadow table for OrderEntity (@AuditTable("orders_aud")): one row per revision,
-- holding the entity's column values as of that revision plus revtype (0=ADD, 1=MOD, 2=DEL).
CREATE TABLE orders_aud (
    id          VARCHAR(36)   NOT NULL,
    rev         BIGINT        NOT NULL REFERENCES revinfo (rev),
    revtype     SMALLINT,
    product_id  VARCHAR(64),
    quantity    INT,
    amount      NUMERIC(18,2),
    status      VARCHAR(20),
    created_at  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id, rev)
);
CREATE INDEX idx_orders_aud_id ON orders_aud (id);
