\connect request_db
INSERT INTO replica_users (id, store_id, first_name, last_name, role, current_status)
VALUES ('22222222-2222-2222-2222-222222222222',NULL,'Test','Consultant','CONSULTANT','OFFLINE')
ON CONFLICT (id) DO UPDATE SET current_status='OFFLINE', role='CONSULTANT';

INSERT INTO replica_user_departments (user_id, department_id)
VALUES ('22222222-2222-2222-2222-222222222222','33333333-3333-3333-3333-333333333333')
ON CONFLICT DO NOTHING;

INSERT INTO requests (id, store_id, department_id, qr_code_id, assigned_user_id, status, client_session_token, created_at, assigned_at, completed_at, version)
VALUES ('44444444-4444-4444-4444-444444444444', '66666666-6666-6666-6666-666666666666', '33333333-3333-3333-3333-333333333333', NULL, NULL, 'CREATED', '55555555-5555-5555-5555-555555555555', now(), NULL, NULL, 0)
ON CONFLICT (id) DO UPDATE SET status='CREATED', assigned_user_id=NULL, assigned_at=NULL;
