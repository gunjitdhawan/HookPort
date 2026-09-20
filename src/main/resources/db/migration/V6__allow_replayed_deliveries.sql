ALTER TABLE webhook_deliveries
DROP CONSTRAINT uq_delivery_event_endpoint;

CREATE UNIQUE INDEX uq_original_delivery_event_endpoint
    ON webhook_deliveries(event_id, endpoint_id)
    WHERE replayed_from_delivery_id IS NULL;