CREATE TABLE order_stats (
    order_id   VARCHAR(36) PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
