CREATE TABLE webhook_events
(
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_webhook_events_idempotency_key
        UNIQUE (idempotency_key)
);

CREATE TABLE webhook_deliveries
(
    id              UUID PRIMARY KEY,
    event_id        UUID        NOT NULL,
    endpoint_id     UUID        NOT NULL,
    status          VARCHAR(30) NOT NULL,
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_deliveries_event
        FOREIGN KEY (event_id)
            REFERENCES webhook_events (id),

    CONSTRAINT fk_deliveries_endpoint
        FOREIGN KEY (endpoint_id)
            REFERENCES webhook_endpoints (id),

    CONSTRAINT uq_delivery_event_endpoint
        UNIQUE (event_id, endpoint_id),

    CONSTRAINT webhook_delivery_status_check
        CHECK (
            status IN (
                       'PENDING',
                       'IN_PROGRESS',
                       'DELIVERED',
                       'RETRY_SCHEDULED',
                       'EXHAUSTED'
                )
            ),

    CONSTRAINT webhook_delivery_attempt_count_check
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_deliveries_pending
    ON webhook_deliveries (status, next_attempt_at);

CREATE INDEX idx_deliveries_endpoint_created
    ON webhook_deliveries (endpoint_id, created_at DESC);