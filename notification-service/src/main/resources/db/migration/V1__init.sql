CREATE TABLE notification (
    id         VARCHAR(36) PRIMARY KEY,
    order_id   VARCHAR(36) NOT NULL,
    type       VARCHAR(32) NOT NULL,
    detail     VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Dedupe table: sending a notification is a pure side-effect with no natural domain key,
-- so we guard it explicitly by messageId.
CREATE TABLE processed_messages (
    message_id   VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
