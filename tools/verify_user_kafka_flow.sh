#!/usr/bin/env bash
set -eu
STORE_ID="11111111-1111-1111-1111-111111111111"
MGR_ID="33333333-3333-3333-3333-333333333333"
DEPT_ID="22222222-2222-2222-2222-222222222222"
MGR_PHONE="+70001111111"
NEW_PHONE="+70009$(date +%H%M%S)"
GATEWAY="${GATEWAY:-http://127.0.0.1:8180}"

echo "=== Login (manager) ==="
RESP=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"phone_number\":\"$MGR_PHONE\",\"password\":\"password\"}")
HTTP_CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
echo "HTTP $HTTP_CODE"
if [ "$HTTP_CODE" != "200" ]; then echo "$BODY"; exit 1; fi
TOKEN=$(echo "$BODY" | grep -o '"access_token":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "${TOKEN:-}" ]; then echo "No accessToken"; echo "$BODY"; exit 1; fi

echo "=== POST /users (create consultant) ==="
CRE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY/api/v1/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Store-Id: $STORE_ID" \
  -H "Content-Type: application/json" \
  -d "{\"phone_number\":\"$NEW_PHONE\",\"password\":\"password\",\"first_name\":\"Kafka\",\"last_name\":\"Test\",\"role\":\"CONSULTANT\",\"department_ids\":[\"$DEPT_ID\"]}")
CREHTTP=$(echo "$CRE" | tail -n1)
CREB=$(echo "$CRE" | sed '$d')
echo "HTTP $CREHTTP body: $(echo "$CREB" | head -c 500)"

echo "=== Last lines auth-service (UserEvent) ==="
docker logs retailhub-auth-service-1 2>&1 | tail -n 25

echo "=== Credential for new phone ==="
docker exec retailhub-postgres-1 psql -U retailhub -d auth_db -t -c \
  "SELECT user_id, phone_number, role FROM credentials WHERE phone_number = '$NEW_PHONE';" 2>/dev/null || true

echo "=== Sample Kafka messages (user-events, last 5 from beginning) ==="
docker exec retailhub-kafka-1 kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic user-events \
  --from-beginning \
  --max-messages 8 \
  --timeout-ms 20000 2>/dev/null || echo "(consumer timeout or error)"
