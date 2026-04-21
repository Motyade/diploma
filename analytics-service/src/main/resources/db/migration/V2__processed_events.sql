CREATE TABLE processed_events (
    consumer_group VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_group, event_id)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);
