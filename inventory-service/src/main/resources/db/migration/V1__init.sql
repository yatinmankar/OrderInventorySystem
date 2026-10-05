CREATE TABLE product_stock (
    product_id VARCHAR(64) PRIMARY KEY,
    available  INT NOT NULL
);

-- Reservation keyed by order_id => natural idempotency key for reserve/release.
CREATE TABLE reservation (
    order_id   VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL,
    quantity   INT NOT NULL,
    status     VARCHAR(16) NOT NULL,  -- RESERVED | FAILED | RELEASED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Seed demo stock. SKU-1 has plenty; SKU-OUT is empty to demo the failure/cancel path.
INSERT INTO product_stock (product_id, available) VALUES ('SKU-1', 100);
INSERT INTO product_stock (product_id, available) VALUES ('SKU-OUT', 0);
