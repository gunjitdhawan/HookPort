ALTER TABLE webhook_deliveries
    ADD COLUMN replayed_from_delivery_id UUID;

ALTER TABLE webhook_deliveries
    ADD CONSTRAINT fk_delivery_replayed_from
        FOREIGN KEY (replayed_from_delivery_id)
            REFERENCES webhook_deliveries(id);

CREATE INDEX idx_deliveries_replayed_from
    ON webhook_deliveries(replayed_from_delivery_id);