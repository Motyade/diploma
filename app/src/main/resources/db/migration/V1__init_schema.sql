-- ============================================================================
-- V1: Init Schema (Stores, Users, Departments, Dispatch, Notifications)
-- ============================================================================
-- Объединённая миграция включающая:
-- 1. Магазины и отделы
-- 2. Пользователи и смены
-- 3. Диспетчеризация (QR, Заявки)
-- 4. Уведомления и устройства
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. STORES & DEPARTMENTS
-- ---------------------------------------------------------------------------

CREATE TABLE stores (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(255) NOT NULL,
    address               TEXT NOT NULL,
    timezone              VARCHAR(50) NOT NULL DEFAULT 'Europe/Moscow',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE stores IS 'Физические магазины - центральная сущность multi-tenant архитектуры';

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id    UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE departments IS 'Отделы магазина - к ним привязываются QR-коды и компетенции консультантов';
CREATE INDEX idx_departments_store_id ON departments(store_id);

-- ---------------------------------------------------------------------------
-- 2. USERS & STAFFING
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id         UUID NOT NULL REFERENCES stores(id) ON DELETE RESTRICT,
    phone_number     VARCHAR(20) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    role             VARCHAR(20) NOT NULL CHECK (role IN ('MANAGER', 'CONSULTANT')),
    current_status   VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' CHECK (current_status IN ('OFFLINE', 'ACTIVE', 'BUSY')),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'Сотрудники магазинов: менеджеры и консультанты';
CREATE INDEX idx_users_store ON users(store_id);

CREATE TABLE department_employees (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    department_id   UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    assigned_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, department_id)
);

COMMENT ON TABLE department_employees IS 'Матрица компетенций: какой консультант в каких отделах может работать';
CREATE INDEX idx_dept_employees_department ON department_employees(department_id);

CREATE TABLE shifts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id      UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    started_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at      TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE shifts IS 'Смены консультантов - простая модель start/stop';
CREATE INDEX idx_shifts_user ON shifts(user_id);
CREATE INDEX idx_shifts_store_active ON shifts(store_id) WHERE ended_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3. DISPATCH (QR & REQUESTS)
-- ---------------------------------------------------------------------------

CREATE TABLE qr_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id   UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    token           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    label           VARCHAR(255),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE qr_codes IS 'QR-коды - точки входа. Клиент сканирует -> создаётся заявка';

CREATE TABLE requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id              UUID NOT NULL REFERENCES stores(id) ON DELETE RESTRICT,
    department_id         UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    qr_code_id            UUID REFERENCES qr_codes(id) ON DELETE SET NULL,
    assigned_user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED', 'ASSIGNED', 'COMPLETED')),
    client_session_token  UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    assigned_at           TIMESTAMP WITH TIME ZONE,
    completed_at          TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE requests IS 'Заявки на обслуживание - ядро диспетчеризации';
CREATE INDEX idx_requests_store_department_status ON requests(store_id, department_id, status);
CREATE INDEX idx_requests_client_session ON requests(client_session_token);

-- ---------------------------------------------------------------------------
-- 4. NOTIFICATIONS
-- ---------------------------------------------------------------------------

CREATE TABLE user_devices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token    VARCHAR(500) NOT NULL,
    device_info  VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, fcm_token)
);

COMMENT ON TABLE user_devices IS 'Реестр FCM-токенов для push-уведомлений';

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    type        VARCHAR(50) NOT NULL,
    payload     JSONB,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE notifications IS 'Inbox уведомлений';
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
