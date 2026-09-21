CREATE TABLE event_outbox (
                              event_id UUID PRIMARY KEY
                                  REFERENCES webhook_events(id),
                              topic VARCHAR(100) NOT NULL,
                              payload TEXT NOT NULL,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                              published_at TIMESTAMPTZ
);

CREATE INDEX idx_event_outbox_unpublished
    ON event_outbox(created_at)
    WHERE published_at IS NULL;