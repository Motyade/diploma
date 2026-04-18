\connect auth_db
TRUNCATE TABLE credentials RESTART IDENTITY CASCADE;

\connect store_db
TRUNCATE TABLE qr_codes, departments, stores RESTART IDENTITY CASCADE;

\connect user_db
TRUNCATE TABLE shifts, department_employees, replica_departments, replica_stores, users RESTART IDENTITY CASCADE;

\connect request_db
TRUNCATE TABLE outbox_events, requests, replica_user_departments, replica_qr_codes, replica_users RESTART IDENTITY CASCADE;

\connect notification_db
TRUNCATE TABLE notifications, user_devices, replica_user_departments, replica_users RESTART IDENTITY CASCADE;

\connect analytics_db
TRUNCATE TABLE fact_requests, dim_users, dim_departments, dim_stores RESTART IDENTITY CASCADE;

\connect user_db
INSERT INTO users (id, store_id, phone_number, password_hash, first_name, last_name, role, current_status)
VALUES ('11111111-1111-1111-1111-111111111111', NULL, '+70001111111', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi5uH8Q2K7S6x9Ejo4kKDAdAm8N1sC.', 'System', 'Manager', 'MANAGER', 'OFFLINE');

\connect auth_db
INSERT INTO credentials (id, user_id, phone_number, password_hash, role, store_id)
VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '+70001111111', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi5uH8Q2K7S6x9Ejo4kKDAdAm8N1sC.', 'MANAGER', NULL);

\connect request_db
INSERT INTO replica_users (id, store_id, first_name, last_name, role, current_status)
VALUES ('11111111-1111-1111-1111-111111111111', NULL, 'System', 'Manager', 'MANAGER', 'OFFLINE');

\connect notification_db
INSERT INTO replica_users (id, store_id, role, current_status)
VALUES ('11111111-1111-1111-1111-111111111111', NULL, 'MANAGER', 'OFFLINE');

\connect analytics_db
INSERT INTO dim_users (id, store_id, first_name, last_name, role)
VALUES ('11111111-1111-1111-1111-111111111111', NULL, 'System', 'Manager', 'MANAGER');
