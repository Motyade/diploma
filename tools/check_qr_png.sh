#!/usr/bin/env bash
set -eu
# Run on Linux server (test QR id from seed / your DB)
QR_ID="${1:-66666666-6666-6666-6666-666666666666}"
STORE="${STORE_ID:-11111111-1111-1111-1111-111111111111}"
BODY=$(curl -s -X POST http://127.0.0.1:8180/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone_number":"+70001111111","password":"password"}')
TOKEN=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || true)
if [ -z "${TOKEN:-}" ]; then echo "login failed: $BODY"; exit 1; fi
code=$(curl -s -o /tmp/qr.png -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Store-Id: $STORE" \
  "http://127.0.0.1:8180/api/v1/qr-codes/$QR_ID")
echo "HTTP $code png_bytes=$(wc -c </tmp/qr.png 2>/dev/null || echo 0)"
exit 0
