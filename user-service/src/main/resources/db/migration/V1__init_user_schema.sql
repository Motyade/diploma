CREATE TABLE replica_stores (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    timezone VARCHAR(50)
);

CREATE TABLE replica_departments (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE users (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    store_id UUID,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('MANAGER', 'CONSULTANT')),
    current_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' CHECK (current_status IN ('OFFLINE', 'ACTIVE', 'BUSY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_store ON users(store_id);

CREATE TABLE department_employees (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    department_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, department_id)
);
CREATE INDEX idx_dept_employees_department ON department_employees(department_id);

CREATE TABLE shifts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    penalties_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_shifts_user ON shifts(user_id);
CREATE INDEX idx_shifts_store_active ON shifts(store_id) WHERE ended_at IS NULL;
