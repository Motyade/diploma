#!/bin/bash
BASE="http://localhost:8180/api/v1"

echo "=== FULL API TEST ==="
echo ""

# Step 1: Login (should now have storeId since store was created)
echo "=== 1. LOGIN ==="
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"phone_number":"+70001111111","password":"manager123"}')
echo "$LOGIN_RESP" | python3 -m json.tool 2>/dev/null || echo "$LOGIN_RESP"
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "LOGIN FAILED"
  exit 1
fi
echo "TOKEN obtained (${#TOKEN} chars)"

# Decode JWT payload
echo ""
echo "=== JWT Payload ==="
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null; echo ""

AUTH="Authorization: Bearer $TOKEN"

# Test all endpoints
echo ""
echo "=== 2. GET /users/me ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/users/me")
echo "$RESP"

echo ""
echo "=== 3. GET /stores/my ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/stores/my")
echo "$RESP"

echo ""
echo "=== 4. GET /stores/my/departments ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/stores/my/departments")
echo "$RESP"

echo ""
echo "=== 5. GET /users ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/users")
echo "$RESP"

echo ""
echo "=== 6. GET /users?role=CONSULTANT ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/users?role=CONSULTANT")
echo "$RESP"

echo ""
echo "=== 7. GET /shifts/active ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/shifts/active")
echo "$RESP"

echo ""
echo "=== 8. GET /shifts/my ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/shifts/my")
echo "$RESP"

echo ""
echo "=== 9. GET /requests ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/requests")
echo "$RESP"

echo ""
echo "=== 10. GET /notifications ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/notifications")
echo "$RESP"

echo ""
echo "=== 11. GET /analytics/dashboard ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/analytics/dashboard")
echo "$RESP"

echo ""
echo "=== 12. GET /analytics/consultants (date format) ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/analytics/consultants?dateFrom=2026-01-01&dateTo=2026-12-31")
echo "$RESP"

echo ""
echo "=== 13. GET /analytics/consultants (datetime format) ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/analytics/consultants?dateFrom=2026-01-01T00:00:00Z&dateTo=2026-12-31T23:59:59Z")
echo "$RESP"

echo ""
echo "=== 14. GET /analytics/requests ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/analytics/requests")
echo "$RESP"

echo ""
echo "=== 15. GET /qr-codes ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/qr-codes")
echo "$RESP"

echo ""
echo "=== 16. POST /devices ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X POST "$BASE/devices" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"fcm_token":"test-fcm-token-456","device_info":"Test Device 2"}')
echo "$RESP"

echo ""
echo "=== 17. POST /stores/my/departments ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X POST "$BASE/stores/my/departments" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Electronics","description":"TV, laptops, phones"}')
echo "$RESP"

echo ""
echo "=== 18. POST /users (create consultant) ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X POST "$BASE/users" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"phone_number":"+70002222222","password":"consultant123","first_name":"Test","last_name":"Consultant","role":"CONSULTANT"}')
echo "$RESP"

echo ""
echo "=== 19. POST /qr-codes ==="
DEPT_ID=$(curl -s -H "$AUTH" "$BASE/stores/my/departments" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id'] if d else '')" 2>/dev/null)
if [ -n "$DEPT_ID" ]; then
  echo "Using department_id=$DEPT_ID"
  RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X POST "$BASE/qr-codes" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"department_id\":\"$DEPT_ID\",\"label\":\"Test QR\"}")
  echo "$RESP"
else
  echo "No department found, skipping"
fi

echo ""
echo "=== 20. PUT /stores/my ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X PUT "$BASE/stores/my" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Updated Store","address":"New Address 123"}')
echo "$RESP"

echo ""
echo "=== 21. POST /shifts/start ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X POST "$BASE/shifts/start" \
  -H "$AUTH")
echo "$RESP"

echo ""
echo "=== 22. POST /auth/refresh ==="
REFRESH=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('refresh_token',''))" 2>/dev/null)
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X POST "$BASE/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refresh_token\":\"$REFRESH\"}")
echo "$RESP"

echo ""
echo "=== 23. GET /qr-codes/scan/{token} - test public endpoint ==="
QR_TOKEN=$(curl -s -H "$AUTH" "$BASE/qr-codes" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['token'] if d else '')" 2>/dev/null)
if [ -n "$QR_TOKEN" ]; then
  echo "Using qr_token=$QR_TOKEN"
  RESP=$(curl -s -w '\n---HTTP:%{http_code}' "$BASE/qr-codes/scan/$QR_TOKEN")
  echo "$RESP"
else
  echo "No QR codes found"
fi

echo ""
echo "=== 24. POST /requests (public) ==="
if [ -n "$QR_TOKEN" ]; then
  RESP=$(curl -s -w '\n---HTTP:%{http_code}' -X POST "$BASE/requests" \
    -H 'Content-Type: application/json' \
    -d "{\"qr_token\":\"$QR_TOKEN\"}")
  echo "$RESP"
else
  echo "No QR token, skipping"
fi

echo ""
echo "=== 25. GET /shifts/my?date_from=2026-01-01&date_to=2026-12-31 ==="
RESP=$(curl -s -w '\n---HTTP:%{http_code}' -H "$AUTH" "$BASE/shifts/my?date_from=2026-01-01&date_to=2026-12-31")
echo "$RESP"

echo ""
echo "=== FULL TEST DONE ==="
