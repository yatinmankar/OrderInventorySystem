-- Every record that ended up on a "<topic>.DLT" topic, persisted for reconciliation.
-- Business identifiers (order_id, message_id) are extracted from the payload so a DLT row can be
-- joined to saga_instance / orders. The raw payload is kept verbatim so it can be replayed.
CREATE TABLE dead_letter_message (
    id                   BIGSERIAL    PRIMARY KEY,

    -- where the DLT record itself lives (idempotency key for the recorder)
    dlt_topic            VARCHAR(255) NOT NULL,
    dlt_partition        INT          NOT NULL,
    dlt_offset           BIGINT       NOT NULL,

    -- where the message originally failed
    original_topic       VARCHAR(255),
    original_partition   INT,
    original_offset      BIGINT,

    -- business identifiers (best effort, NULL if the payload was not parseable)
    order_id             VARCHAR(64),
    message_id           VARCHAR(128),
    message_key          VARCHAR(255),

    payload              TEXT,

    -- why it failed
    exception_class      VARCHAR(512),
    exception_message    TEXT,
    exception_stacktrace TEXT,

    -- reconciliation workflow: NEW -> REPLAYED | RESOLVED | DISCARDED
    status               VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    resolution_note      TEXT,
    resolved_at          TIMESTAMP WITH TIME ZONE,

    received_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_dead_letter_dlt_record UNIQUE (dlt_topic, dlt_partition, dlt_offset)
);
CREATE INDEX idx_dead_letter_status   ON dead_letter_message (status, received_at);
CREATE INDEX idx_dead_letter_order_id ON dead_letter_message (order_id);
