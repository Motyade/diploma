CREATE TABLE replica_qr_codes (
    id            UUID PRIMARY KEY,
    department_id UUID         NOT NULL,
    store_id      UUID         NOT NULL,
    token         UUID         NOT NULL UNIQUE,
    label         VARCHAR(255),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE replica_users (
    id             UUID PRIMARY KEY,
    store_id       UUID,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    role           VARCHAR(20),
    current_status VARCHAR(20) DEFAULT 'OFFLINE'
);

CREATE TABLE replica_user_departments (
    id            UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       UUID NOT NULL,
    department_id UUID NOT NULL,
    UNIQUE (user_id, department_id)
);

CREATE TABLE requests (
    id                   UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    store_id             UUID        NOT NULL,
    department_id        UUID        NOT NULL,
    qr_code_id           UUID,
    assigned_user_id     UUID,
    status               VARCHAR(20) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED','WAITING','ESCALATED','ASSIGNED','COMPLETED','CANCELED')),
    client_session_token UUID        DEFAULT gen_random_uuid() NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_at          TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    escalated_at         TIMESTAMPTZ,
    version              BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_requests_store_dept_status ON requests (store_id, department_id, status);
CREATE INDEX idx_requests_session           ON requests (client_session_token);
CREATE INDEX idx_requests_status            ON requests (status);

CREATE TABLE outbox_events (
    id             UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
