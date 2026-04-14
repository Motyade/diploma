#!/bin/bash
set -uo pipefail

BASE="http://localhost"
PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

check() {
    local label="$1"
    local expected="$2"
    local actual="$3"
    TOTAL=$((TOTAL+1))
    if [ "$actual" = "$expected" ]; then
        echo -e "${GREEN}[PASS]${NC} $label (HTTP $actual)"
        PASS=$((PASS+1))
    else
        echo -e "${RED}[FAIL]${NC} $label (expected $expected, got $actual)"
        FAIL=$((FAIL+1))
    fi
}

check_range() {
    local label="$1"
    local min="$2"
    local max="$3"
    local actual="$4"
    TOTAL=$((TOTAL+1))
    if [ "$actual" -ge "$min" ] 2>/dev/null && [ "$actual" -le "$max" ] 2>/dev/null; then
        echo -e "${GREEN}[PASS]${NC} $label (HTTP $actual)"
        PASS=$((PASS+1))
    else
        echo -e "${RED}[FAIL]${NC} $label (expected ${min}-${max}, got $actual)"
        FAIL=$((FAIL+1))
    fi
}

echo "============================================"
echo "  RetailHub Microservices API Test Suite"
echo "============================================"
echo ""

# ─── PHASE 0: Seed Data ─────────────────────────────────────
echo -e "${YELLOW}>>> Phase 0: Seed data${NC}"

STORE_ID="11111111-1111-1111-1111-111111111111"
DEPT_ID="22222222-2222-2222-2222-222222222222"
MGR_ID="33333333-3333-3333-3333-333333333333"
CONS_ID="44444444-4444-4444-4444-444444444444"
QR_TOKEN="55555555-5555-5555-5555-555555555555"
QR_ID="66666666-6666-6666-6666-666666666666"
MGR_PHONE="+70001111111"
CONS_PHONE="+70002222222"
PASSWORD="testpass123"
HASH='$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG'

docker exec diplom-postgres-1 psql -U retailhub -d store_db -c "
INSERT INTO stores (id, name, address, timezone) VALUES ('$STORE_ID', 'Test Store', 'Test Address 1', 'Europe/Moscow') ON CONFLICT (id) DO NOTHING;
INSERT INTO departments (id, store_id, name, description) VALUES ('$DEPT_ID', '$STORE_ID', 'Electronics', 'Test dept') ON CONFLICT (id) DO NOTHING;
INSERT INTO qr_codes (id, department_id, token, label, is_active) VALUES ('$QR_ID', '$DEPT_ID', '$QR_TOKEN', 'TestQR', true) ON CONFLICT (id) DO NOTHING;
" 2>/dev/null

docker exec diplom-postgres-1 psql -U retailhub -d user_db -c "
INSERT INTO replica_stores (id, name, address, timezone) VALUES ('$STORE_ID', 'Test Store', 'Test Address 1', 'Europe/Moscow') ON CONFLICT (id) DO NOTHING;
INSERT INTO replica_departments (id, store_id, name, description) VALUES ('$DEPT_ID', '$STORE_ID', 'Electronics', 'Test dept') ON CONFLICT (id) DO NOTHING;
INSERT INTO users (id, store_id, phone_number, password_hash, first_name, last_name, role, current_status) VALUES ('$MGR_ID', '$STORE_ID', '$MGR_PHONE', '$HASH', 'Test', 'Manager', 'MANAGER', 'ACTIVE') ON CONFLICT (id) DO NOTHING;
INSERT INTO users (id, store_id, phone_number, password_hash, first_name, last_name, role, current_status) VALUES ('$CONS_ID', '$STORE_ID', '$CONS_PHONE', '$HASH', 'Test', 'Consultant', 'CONSULTANT', 'OFFLINE') ON CONFLICT (id) DO NOTHING;
INSERT INTO department_employees (id, user_id, department_id) VALUES (gen_random_uuid(), '$CONS_ID', '$DEPT_ID') ON CONFLICT DO NOTHING;
" 2>/dev/null

docker exec diplom-postgres-1 psql -U retailhub -d auth_db -c "
INSERT INTO credentials (id, user_id, phone_number, password_hash, role, store_id) VALUES (gen_random_uuid(), '$MGR_ID', '$MGR_PHONE', '$HASH', 'MANAGER', '$STORE_ID') ON CONFLICT DO NOTHING;
INSERT INTO credentials (id, user_id, phone_number, password_hash, role, store_id) VALUES (gen_random_uuid(), '$CONS_ID', '$CONS_PHONE', '$HASH', 'CONSULTANT', '$STORE_ID') ON CONFLICT DO NOTHING;
" 2>/dev/null

docker exec diplom-postgres-1 psql -U retailhub -d request_db -c "
INSERT INTO replica_qr_codes (id, department_id, store_id, token, label, is_active) VALUES ('$QR_ID', '$DEPT_ID', '$STORE_ID', '$QR_TOKEN', 'TestQR', true) ON CONFLICT (id) DO NOTHING;
INSERT INTO replica_users (id, store_id, first_name, last_name, role, current_status) VALUES ('$CONS_ID', '$STORE_ID', 'Test', 'Consultant', 'CONSULTANT', 'OFFLINE') ON CONFLICT (id) DO NOTHING;
INSERT INTO replica_users (id, store_id, first_name, last_name, role, current_status) VALUES ('$MGR_ID', '$STORE_ID', 'Test', 'Manager', 'MANAGER', 'ACTIVE') ON CONFLICT (id) DO NOTHING;
INSERT INTO replica_user_departments (id, user_id, department_id) VALUES (gen_random_uuid(), '$CONS_ID', '$DEPT_ID') ON CONFLICT DO NOTHING;
" 2>/dev/null

docker exec diplom-postgres-1 psql -U retailhub -d notification_db -c "
INSERT INTO replica_users (id, store_id, role, current_status) VALUES ('$CONS_ID', '$STORE_ID', 'CONSULTANT', 'OFFLINE') ON CONFLICT (id) DO NOTHING;
INSERT INTO replica_users (id, store_id, role, current_status) VALUES ('$MGR_ID', '$STORE_ID', 'MANAGER', 'ACTIVE') ON CONFLICT (id) DO NOTHING;
INSERT INTO replica_user_departments (id, user_id, department_id) VALUES (gen_random_uuid(), '$CONS_ID', '$DEPT_ID') ON CONFLICT DO NOTHING;
" 2>/dev/null

echo "Seed data inserted."
echo ""

# ─── PHASE 1: Auth Service ──────────────────────────────────
echo -e "${YELLOW}>>> Phase 1: Auth Service (8081)${NC}"

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE:8081/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$MGR_PHONE\",\"password\":\"password\"}")
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "POST /auth/login (manager)" "200" "$HTTP_CODE"
MGR_TOKEN=$(echo "$BODY" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE:8081/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$CONS_PHONE\",\"password\":\"password\"}")
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "POST /auth/login (consultant)" "200" "$HTTP_CODE"
CONS_TOKEN=$(echo "$BODY" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)
REFRESH_TOKEN=$(echo "$BODY" | grep -o '"refreshToken":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "${REFRESH_TOKEN:-}" ]; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8081/api/v1/auth/refresh" \
      -H "Content-Type: application/json" \
      -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
    check "POST /auth/refresh" "200" "$HTTP_CODE"
else
    check "POST /auth/refresh" "200" "SKIP_NO_TOKEN"
fi

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8081/api/v1/auth/me" \
  -H "X-User-Id: $MGR_ID")
check "GET /auth/me" "200" "$HTTP_CODE"
echo ""

# ─── PHASE 2: Store Service ─────────────────────────────────
echo -e "${YELLOW}>>> Phase 2: Store Service (8082)${NC}"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8082/api/v1/stores/my" \
  -H "X-Store-Id: $STORE_ID" -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER")
check "GET /stores/my" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE:8082/api/v1/stores/my" \
  -H "Content-Type: application/json" -H "X-Store-Id: $STORE_ID" -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER" \
  -d '{"name":"Test Store Updated","address":"Updated Address"}')
check "PUT /stores/my" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8082/api/v1/stores/my/departments" \
  -H "X-Store-Id: $STORE_ID" -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER")
check "GET /stores/my/departments" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8082/api/v1/departments/$DEPT_ID" \
  -H "X-Store-Id: $STORE_ID")
check "GET /departments/{id}" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8082/api/v1/qr-codes?departmentId=$DEPT_ID" \
  -H "X-Store-Id: $STORE_ID")
check "GET /qr-codes?departmentId" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8082/api/v1/qr-codes/scan/$QR_TOKEN")
check "GET /qr-codes/scan/{token} (public)" "200" "$HTTP_CODE"
echo ""

# ─── PHASE 3: User Service ──────────────────────────────────
echo -e "${YELLOW}>>> Phase 3: User Service (8083)${NC}"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8083/api/v1/users" \
  -H "X-Store-Id: $STORE_ID" -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER")
check "GET /users" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8083/api/v1/users/$CONS_ID" \
  -H "X-Store-Id: $STORE_ID" -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER")
check "GET /users/{id}" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8083/api/v1/shifts/start" \
  -H "X-User-Id: $CONS_ID" -H "X-Store-Id: $STORE_ID" -H "X-Role: CONSULTANT")
check_range "POST /shifts/start" "200" "201" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8083/api/v1/shifts/active" \
  -H "X-Store-Id: $STORE_ID" -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER")
check "GET /shifts/active" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8083/api/v1/shifts/my" \
  -H "X-User-Id: $CONS_ID" -H "X-Store-Id: $STORE_ID" -H "X-Role: CONSULTANT")
check "GET /shifts/my" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8083/api/v1/shifts/end" \
  -H "X-User-Id: $CONS_ID" -H "X-Store-Id: $STORE_ID" -H "X-Role: CONSULTANT")
check "POST /shifts/end" "200" "$HTTP_CODE"

# Restart shift for request tests
curl -s -o /dev/null -X POST "$BASE:8083/api/v1/shifts/start" \
  -H "X-User-Id: $CONS_ID" -H "X-Store-Id: $STORE_ID" -H "X-Role: CONSULTANT"

# Update consultant status to ACTIVE in request_db replica
docker exec diplom-postgres-1 psql -U retailhub -d request_db -c \
  "UPDATE replica_users SET current_status='ACTIVE' WHERE id='$CONS_ID';" 2>/dev/null
echo ""

# ─── PHASE 4: Request Service ───────────────────────────────
echo -e "${YELLOW}>>> Phase 4: Request Service (8084)${NC}"

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE:8084/api/v1/requests" \
  -H "Content-Type: application/json" \
  -d "{\"qrToken\":\"$QR_TOKEN\"}")
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "POST /requests (create)" "201" "$HTTP_CODE"
REQ_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
SESSION=$(echo "$BODY" | grep -o '"clientSessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "${REQ_ID:-}" ] && [ -n "${SESSION:-}" ]; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8084/api/v1/requests/$REQ_ID?session=$SESSION")
    check "GET /requests/{id}?session (client polling)" "200" "$HTTP_CODE"
else
    check "GET /requests/{id}?session (client polling)" "200" "SKIP"
fi

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8084/api/v1/requests?page=0&size=10" \
  -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER" -H "X-Store-Id: $STORE_ID")
check "GET /requests (list, manager)" "200" "$HTTP_CODE"

if [ -n "${REQ_ID:-}" ]; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8084/api/v1/requests/$REQ_ID/assign" \
      -H "X-User-Id: $CONS_ID" -H "X-Role: CONSULTANT" -H "X-Store-Id: $STORE_ID")
    check "POST /requests/{id}/assign" "200" "$HTTP_CODE"

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8084/api/v1/requests/$REQ_ID/complete" \
      -H "X-User-Id: $CONS_ID" -H "X-Role: CONSULTANT" -H "X-Store-Id: $STORE_ID")
    check "POST /requests/{id}/complete" "200" "$HTTP_CODE"
fi

# Second request: cancel flow
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE:8084/api/v1/requests" \
  -H "Content-Type: application/json" -d "{\"qrToken\":\"$QR_TOKEN\"}")
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check "POST /requests (create 2nd)" "201" "$HTTP_CODE"
REQ2_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
SESSION2=$(echo "$BODY" | grep -o '"clientSessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "${REQ2_ID:-}" ] && [ -n "${SESSION2:-}" ]; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8084/api/v1/requests/$REQ2_ID/cancel?session=$SESSION2")
    check "POST /requests/{id}/cancel" "200" "$HTTP_CODE"
fi
echo ""

# ─── PHASE 5: Notification Service ──────────────────────────
echo -e "${YELLOW}>>> Phase 5: Notification Service (8085)${NC}"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8085/api/v1/notifications" \
  -H "X-User-Id: $CONS_ID")
check "GET /notifications" "200" "$HTTP_CODE"

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE:8085/api/v1/devices" \
  -H "Content-Type: application/json" -H "X-User-Id: $CONS_ID" \
  -d '{"fcmToken":"test-fcm-token-123","deviceInfo":"Test Device"}')
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
check_range "POST /devices (register FCM)" "200" "201" "$HTTP_CODE"
DEVICE_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "${DEVICE_ID:-}" ]; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE:8085/api/v1/devices/$DEVICE_ID" \
      -H "X-User-Id: $CONS_ID")
    check_range "DELETE /devices/{id}" "200" "204" "$HTTP_CODE"
fi
echo ""

# ─── PHASE 6: Analytics Service ─────────────────────────────
echo -e "${YELLOW}>>> Phase 6: Analytics Service (8086)${NC}"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8086/api/v1/analytics/dashboard?period=today" \
  -H "X-Store-Id: $STORE_ID")
check "GET /analytics/dashboard" "200" "$HTTP_CODE"

DATE_FROM=$(date -u +%Y-%m-%dT00:00:00Z)
DATE_TO=$(date -u +%Y-%m-%dT23:59:59Z)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8086/api/v1/analytics/consultants?dateFrom=$DATE_FROM&dateTo=$DATE_TO" \
  -H "X-Store-Id: $STORE_ID")
check "GET /analytics/consultants" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8086/api/v1/analytics/consultants/$CONS_ID?dateFrom=$DATE_FROM&dateTo=$DATE_TO" \
  -H "X-Store-Id: $STORE_ID")
check "GET /analytics/consultants/{id}" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8086/api/v1/analytics/requests?page=0&size=20" \
  -H "X-Store-Id: $STORE_ID")
check "GET /analytics/requests" "200" "$HTTP_CODE"
echo ""

# ─── PHASE 7: API Gateway Proxy ─────────────────────────────
echo -e "${YELLOW}>>> Phase 7: API Gateway Proxy (8180)${NC}"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8180/swagger-ui.html")
check "GET /swagger-ui.html (gateway)" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8180/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$MGR_PHONE\",\"password\":\"password\"}")
check "Gateway -> POST /auth/login" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8180/api/v1/users" \
  -H "Authorization: Bearer ${MGR_TOKEN:-none}" \
  -H "X-Store-Id: $STORE_ID" -H "X-User-Id: $MGR_ID" -H "X-Role: MANAGER")
check "Gateway -> GET /users" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8180/api/v1/analytics/dashboard?period=today" \
  -H "X-Store-Id: $STORE_ID")
check "Gateway -> GET /analytics/dashboard" "200" "$HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8180/api/v1/qr-codes/scan/$QR_TOKEN")
check "Gateway -> GET /qr-codes/scan/{token}" "200" "$HTTP_CODE"
echo ""

# ─── PHASE 8: WebSocket ─────────────────────────────────────
echo -e "${YELLOW}>>> Phase 8: WebSocket (8085)${NC}"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE:8085/ws/info")
check_range "GET /ws/info (SockJS endpoint)" "200" "200" "$HTTP_CODE"
echo ""

# ─── SUMMARY ────────────────────────────────────────────────
echo "============================================"
echo -e "  Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}, $TOTAL total"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
