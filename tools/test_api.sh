#!/bin/bash
BASE="http://localhost:8180/api/v1"

echo "=== 1. LOGIN ==="
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"phone_number":"+70001111111","password":"manager123"}')
echo "$LOGIN_RESP"
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null)
echo "TOKEN=$TOKEN"

if [ -z "$TOKEN" ]; then
  echo "LOGIN FAILED, cannot continue"
  exit 1
fi

AUTH="Authorization: Bearer $TOKEN"

echo ""
echo "=== 2. GET /users/me ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/users/me"

echo ""
echo "=== 3. GET /stores/my ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/stores/my"

echo ""
echo "=== 4. GET /stores/my/departments ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/stores/my/departments"

echo ""
echo "=== 5. POST /stores (create store) ==="
STORE_RESP=$(curl -s -w '\nHTTP:%{http_code}\n' -X POST "$BASE/stores" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Test Store","address":"ul. Test 1","timezone":"Europe/Moscow"}')
echo "$STORE_RESP"

echo ""
echo "=== 6. GET /users ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/users"

echo ""
echo "=== 7. GET /shifts/active ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/shifts/active"

echo ""
echo "=== 8. GET /shifts/my ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/shifts/my"

echo ""
echo "=== 9. GET /requests ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/requests"

echo ""
echo "=== 10. GET /notifications ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/notifications"

echo ""
echo "=== 11. GET /analytics/dashboard ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/analytics/dashboard"

echo ""
echo "=== 12. GET /analytics/consultants?dateFrom=2026-01-01&dateTo=2026-12-31 ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/analytics/consultants?dateFrom=2026-01-01&dateTo=2026-12-31"

echo ""
echo "=== 13. GET /analytics/requests ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/analytics/requests"

echo ""
echo "=== 14. GET /qr-codes ==="
curl -s -w '\nHTTP:%{http_code}\n' -H "$AUTH" "$BASE/qr-codes"

echo ""
echo "=== 15. POST /auth/refresh ==="
REFRESH_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('refresh_token',''))" 2>/dev/null)
curl -s -w '\nHTTP:%{http_code}\n' -X POST "$BASE/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refresh_token\":\"$REFRESH_TOKEN\"}"

echo ""
echo "=== 16. POST /devices ==="
curl -s -w '\nHTTP:%{http_code}\n' -X POST "$BASE/devices" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"fcm_token":"test-fcm-token-123","device_info":"Test Device"}'

echo ""
echo "=== 17. POST /requests (public - need QR token) ==="
curl -s -w '\nHTTP:%{http_code}\n' -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -d '{"qr_token":"00000000-0000-0000-0000-000000000001"}'

echo ""
echo "=== 18. GET /qr-codes/scan/00000000-0000-0000-0000-000000000001 ==="
curl -s -w '\nHTTP:%{http_code}\n' "$BASE/qr-codes/scan/00000000-0000-0000-0000-000000000001"

echo ""
echo "=== DONE ==="
