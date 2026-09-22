CREATE TABLE endpoint_rate_buckets (
                                       endpoint_id UUID PRIMARY KEY
                                           REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
                                       capacity INTEGER NOT NULL DEFAULT 5,
                                       refill_per_second INTEGER NOT NULL DEFAULT 5,
                                       tokens DOUBLE PRECISION NOT NULL DEFAULT 5,
                                       refilled_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                                       CONSTRAINT bucket_capacity_positive CHECK (capacity > 0),
                                       CONSTRAINT bucket_refill_positive CHECK (refill_per_second > 0),
                                       CONSTRAINT bucket_tokens_valid
                                           CHECK (tokens >= 0 AND tokens <= capacity)
);

INSERT INTO endpoint_rate_buckets
(endpoint_id, capacity, refill_per_second, tokens, refilled_at)
SELECT id, 5, 5, 5, now()
FROM webhook_endpoints;