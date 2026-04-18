#!/bin/bash
echo "=== Direct tests bypassing gateway ==="

echo ""
echo "=== store-service: GET /stores/my (no header) ==="
curl -s -w '\nHTTP:%{http_code}\n' http://localhost:8082/api/v1/stores/my 2>&1 || echo "CURL_FAILED"

echo ""
echo "=== store-service: GET /stores/my (X-Store-Id: empty) ==="
curl -s -w '\nHTTP:%{http_code}\n' -H 'X-Store-Id: ' http://localhost:8082/api/v1/stores/my 2>&1 || echo "CURL_FAILED"

echo ""
echo "=== store-service: GET /stores/my (no store exists) via docker network ==="
docker exec retailhub-store-service-1 wget -qO- http://localhost:8082/api/v1/stores/my 2>&1 || echo "WGET_FAILED"

echo ""
echo "=== user-service: GET /users/me (no X-Store-Id, valid X-User-Id) ==="
curl -s -w '\nHTTP:%{http_code}\n' -H 'X-User-Id: 11111111-1111-1111-1111-111111111111' http://localhost:8083/api/v1/users/me 2>&1 || echo "CURL_FAILED"

echo ""
echo "=== user-service: GET /users/me (with X-Store-Id=null string) ==="
curl -s -w '\nHTTP:%{http_code}\n' -H 'X-User-Id: 11111111-1111-1111-1111-111111111111' -H 'X-Store-Id: null' http://localhost:8083/api/v1/users/me 2>&1 || echo "CURL_FAILED"

echo ""
echo "=== Check user store_id in user_db ==="
docker exec retailhub-postgres-1 psql -U retailhub -d user_db -c "SELECT id, store_id, current_status FROM users WHERE id = '11111111-1111-1111-1111-111111111111';"

echo ""
echo "=== Check credential store_id in auth_db ==="
docker exec retailhub-postgres-1 psql -U retailhub -d auth_db -c "SELECT user_id, store_id FROM credentials WHERE user_id = '11111111-1111-1111-1111-111111111111';"

echo ""
echo "=== store-service: GET /stores/my/departments (no header) ==="
curl -s -w '\nHTTP:%{http_code}\n' http://localhost:8082/api/v1/stores/my/departments 2>&1 || echo "CURL_FAILED"

echo ""
echo "=== Check store_db for stores ==="
docker exec retailhub-postgres-1 psql -U retailhub -d store_db -c "SELECT id, name FROM stores;"

echo ""
echo "=== store-service: GET /stores/my with valid store ID ==="
STORE_ID=$(docker exec retailhub-postgres-1 psql -U retailhub -d store_db -t -A -c "SELECT id FROM stores LIMIT 1;" 2>/dev/null)
if [ -n "$STORE_ID" ]; then
    echo "Using store_id=$STORE_ID"
    curl -s -w '\nHTTP:%{http_code}\n' -H "X-Store-Id: $STORE_ID" http://localhost:8082/api/v1/stores/my 2>&1 || echo "CURL_FAILED"
else
    echo "No stores found"
fi

echo ""
echo "=== analytics-service: GET /analytics/consultants with date format ==="
curl -s -w '\nHTTP:%{http_code}\n' "http://localhost:8086/api/v1/analytics/consultants?dateFrom=2026-01-01&dateTo=2026-12-31" -H 'X-Store-Id: 00000000-0000-0000-0000-000000000001' 2>&1

echo ""
echo "=== analytics-service: GET /analytics/consultants with datetime format ==="
curl -s -w '\nHTTP:%{http_code}\n' "http://localhost:8086/api/v1/analytics/consultants?dateFrom=2026-01-01T00:00:00Z&dateTo=2026-12-31T23:59:59Z" -H 'X-Store-Id: 00000000-0000-0000-0000-000000000001' 2>&1

echo ""
echo "=== DIRECT TESTS DONE ==="
