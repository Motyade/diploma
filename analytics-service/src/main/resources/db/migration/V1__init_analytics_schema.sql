CREATE TABLE dim_stores (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    address TEXT,
    timezone VARCHAR(50)
);

CREATE TABLE dim_departments (
    id UUID PRIMARY KEY,
    store_id UUID,
    name VARCHAR(255),
    description TEXT
);

CREATE TABLE dim_users (
    id UUID PRIMARY KEY,
    store_id UUID,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(20)
);

CREATE TABLE fact_requests (
    request_id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    department_id UUID NOT NULL,
    department_name VARCHAR(255),
    assigned_user_id UUID,
    assigned_user_name VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    waiting_at TIMESTAMPTZ,
    escalated_at TIMESTAMPTZ,
    assigned_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    reassigned_count INT NOT NULL DEFAULT 0,
    response_time_seconds BIGINT,
    service_time_seconds BIGINT
);

CREATE INDEX idx_fact_requests_store ON fact_requests(store_id);
CREATE INDEX idx_fact_requests_store_created ON fact_requests(store_id, created_at);
CREATE INDEX idx_fact_requests_user ON fact_requests(assigned_user_id);
CREATE INDEX idx_fact_requests_status ON fact_requests(status);
