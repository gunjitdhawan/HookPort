CREATE TABLE webhook_endpoints
(
    id             UUID PRIMARY KEY,
    name           VARCHAR(100)  NOT NULL,
    target_url     VARCHAR(2048) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    signing_secret VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT webhook_endpoints_status_check
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_webhook_endpoints_name
    ON webhook_endpoints (name);