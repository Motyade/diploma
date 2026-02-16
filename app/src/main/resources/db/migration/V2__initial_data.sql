-- ============================================================================
-- V2: Initial Data (Test Store & Admin User)
-- ============================================================================

-- 1. Create Main Store
INSERT INTO stores (id, name, address, timezone)
VALUES (
    gen_random_uuid(),
    'Main Store',
    'Moscow, Red Square 1',
    'Europe/Moscow'
);

-- 2. Create Admin User (Manager)
-- Password: "password" ($2a$10$...)
INSERT INTO users (id, store_id, phone_number, password_hash, first_name, last_name, role, current_status)
VALUES (
    gen_random_uuid(),
    (SELECT id FROM stores WHERE name = 'Main Store' LIMIT 1),
    '+79990000000',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    'Admin',
    'User',
    'MANAGER',
    'ACTIVE'
);
