CREATE TABLE replica_users (
    id UUID PRIMARY KEY,
    store_id UUID,
    role VARCHAR(20),
    current_status VARCHAR(20) DEFAULT 'OFFLINE'
);

CREATE TABLE replica_user_departments (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL,
    department_id UUID NOT NULL,
    UNIQUE(user_id, department_id)
);
CREATE INDEX idx_rud_department ON replica_user_departments(department_id);

CREATE TABLE user_devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL,
    fcm_token VARCHAR(500) NOT NULL,
    device_info VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, fcm_token)
);

CREATE TABLE notifications (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    type VARCHAR(50) NOT NULL,
    payload JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
