#!/usr/bin/env bash
# Запуск на основном сервере (Linux). Проверка ЖЦ заявки через gateway :8180.
# Сид: консультант 44444444-..., отдел 22222222-..., QR-токен 55555555-...
set -eu
G="${GATEWAY:-http://127.0.0.1:8180}"
QR_TOKEN="55555555-5555-5555-5555-555555555555"
CONS_PHONE="+70002222222"
CONS_PASS="password"
STORE_ID="11111111-1111-1111-1111-111111111111"
CONS_ID="44444444-4444-4444-4444-444444444444"
DEPT_ID="22222222-2222-2222-2222-222222222222"

echo "=== 0) Консультант ACTIVE в request_db + отдел (схема request_db) ==="
docker exec retailhub-postgres-1 psql -U retailhub -d request_db -v ON_ERROR_STOP=1 -c \
  "INSERT INTO replica_users (id, store_id, first_name, last_name, role, current_status)
   VALUES ('$CONS_ID', '$STORE_ID', 'Test', 'Consultant', 'CONSULTANT', 'ACTIVE')
   ON CONFLICT (id) DO UPDATE SET current_status = 'ACTIVE', role = 'CONSULTANT';
   INSERT INTO replica_user_departments (user_id, department_id)
   SELECT '$CONS_ID', '$DEPT_ID'
   WHERE NOT EXISTS (SELECT 1 FROM replica_user_departments WHERE user_id = '$CONS_ID' AND department_id = '$DEPT_ID');" \
  >/dev/null

echo "=== 1) POST заявка (клиент) ==="
CR=$(curl -s -w "\n%{http_code}" -X POST "$G/api/v1/requests" \
  -H "Content-Type: application/json" \
  -d "{\"qr_token\":\"$QR_TOKEN\"}")
HTTP=$(echo "$CR" | tail -n1)
BODY=$(echo "$CR" | sed '$d')
echo "HTTP $HTTP"
if [ "$HTTP" != "201" ]; then echo "$BODY"; exit 1; fi
REQ_ID=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
SESS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['client_session_token'])")
echo "request_id=$REQ_ID session=$SESS status=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"

echo "=== 2) GET заявка (клиент, session) ==="
G1=$(curl -s -w "\n%{http_code}" "$G/api/v1/requests/$REQ_ID?session=$SESS")
echo "HTTP $(echo "$G1" | tail -n1) body=$(echo "$G1" | sed '$d' | head -c 200)..."

echo "=== 3) Логин консультант ==="
LG=$(curl -s -w "\n%{http_code}" -X POST "$G/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"phone_number\":\"$CONS_PHONE\",\"password\":\"$CONS_PASS\"}")
HTL=$(echo "$LG" | tail -n1)
LBL=$(echo "$LG" | sed '$d')
if [ "$HTL" != "200" ]; then echo "login HTTP $HTL $LBL"; exit 1; fi
TOKEN=$(echo "$LBL" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

echo "=== 4) Назначить заявку (консультант) ==="
AS=$(curl -s -w "\n%{http_code}" -X POST "$G/api/v1/requests/$REQ_ID/assign" \
  -H "Authorization: Bearer $TOKEN")
echo "assign HTTP $(echo "$AS" | tail -n1)"
echo "assign body head: $(echo "$AS" | sed '$d' | head -c 300)..."

echo "=== 5) GET заявка после назначения ==="
G2=$(curl -s "$G/api/v1/requests/$REQ_ID?session=$SESS")
echo "$G2" | python3 -c "import sys,json; j=json.load(sys.stdin); print('status=',j.get('status'),'consultant=',j.get('consultant_name'))"

echo "=== 6) Завершить (консультант) ==="
CP=$(curl -s -w "\n%{http_code}" -X POST "$G/api/v1/requests/$REQ_ID/complete" \
  -H "Authorization: Bearer $TOKEN")
echo "complete HTTP $(echo "$CP" | tail -n1)"

echo "=== 7) GET заявка финал ==="
G3=$(curl -s "$G/api/v1/requests/$REQ_ID?session=$SESS")
echo "$G3" | python3 -c "import sys,json; j=json.load(sys.stdin); print('status=',j.get('status'))"

echo "=== 8) Скан-страница (nginx) ==="
curl -s -o /dev/null -w "GET / HTTP %{http_code}\n" http://127.0.0.1/
curl -s -o /dev/null -w "GET /scan/... HTTP %{http_code}\n" "http://127.0.0.1/scan/$QR_TOKEN"

echo "=== OK lifecycle test finished ==="
