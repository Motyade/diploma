CREATE TABLE stores (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Moscow',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE departments (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_departments_store_name UNIQUE (store_id, name)
);
CREATE INDEX idx_departments_store_id ON departments(store_id);

CREATE TABLE qr_codes (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    token UUID DEFAULT gen_random_uuid() NOT NULL UNIQUE,
    label VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
