ALTER TABLE webhook_deliveries
DROP CONSTRAINT webhook_delivery_status_check;

ALTER TABLE webhook_deliveries
    ADD CONSTRAINT webhook_delivery_status_check
        CHECK (
            status IN (
                       'PENDING',
                       'IN_PROGRESS',
                       'DELIVERED',
                       'RETRY_SCHEDULED',
                       'FAILED',
                       'EXHAUSTED'
                )
            );

CREATE TABLE delivery_attempts
(
    id             UUID PRIMARY KEY,
    delivery_id    UUID         NOT NULL,
    attempt_number INTEGER      NOT NULL,
    started_at     TIMESTAMPTZ  NOT NULL,
    completed_at   TIMESTAMPTZ,
    outcome        VARCHAR(30),
    http_status    INTEGER,
    error_message  VARCHAR(1000),
    duration_ms    BIGINT,

    CONSTRAINT fk_attempts_delivery
        FOREIGN KEY (delivery_id)
            REFERENCES webhook_deliveries (id),

    CONSTRAINT uq_delivery_attempt_number
        UNIQUE (delivery_id, attempt_number),

    CONSTRAINT attempt_number_positive
        CHECK (attempt_number > 0),

    CONSTRAINT attempt_outcome_check
        CHECK (
            outcome IS NULL OR outcome IN (
                                           'DELIVERED',
                                           'RETRYABLE_FAILURE',
                                           'PERMANENT_FAILURE'
                )
            )
);

CREATE INDEX idx_delivery_attempts_delivery
    ON delivery_attempts (delivery_id, attempt_number);