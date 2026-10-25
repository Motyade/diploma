CREATE TABLE fact_user_status_intervals (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    store_id UUID,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ
);

CREATE INDEX idx_fusi_user_started ON fact_user_status_intervals(user_id, started_at DESC);
CREATE UNIQUE INDEX uniq_fusi_open_per_user ON fact_user_status_intervals(user_id) WHERE ended_at IS NULL;
