#!/usr/bin/env bash
set -euo pipefail

cd /opt/retailhub

docker compose exec -T postgres psql -U retailhub <<'SQL'
\connect auth_db
INSERT INTO credentials (id, user_id, phone_number, password_hash, role, store_id)
VALUES ('22222222-2222-2222-2222-222222222221','22222222-2222-2222-2222-222222222222','+70002222222','$2a$10$7EqJtq98hPqEX7fNZaFWoOHi5uH8Q2K7S6x9Ejo4kKDAdAm8N1sC.','CONSULTANT',NULL)
ON CONFLICT (phone_number) DO NOTHING;

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
SQL

TOKEN=$(curl -sS -X POST "http://localhost:8180/api/v1/auth/login" -H "Content-Type: application/json" -d '{"phoneNumber":"+70002222222","password":"password"}' | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "LOGIN_FAILED"
  exit 1
fi

HTTP_CODE=$(curl -sS -o /tmp/assign_response.json -w "%{http_code}" -X POST "http://localhost:8180/api/v1/requests/44444444-4444-4444-4444-444444444444/assign" -H "Authorization: Bearer $TOKEN")
echo "HTTP_CODE=$HTTP_CODE"
echo "BODY=$(cat /tmp/assign_response.json)"
