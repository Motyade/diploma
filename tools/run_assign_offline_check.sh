#!/usr/bin/env bash
set -euo pipefail

cd /opt/retailhub

docker compose exec -T request-service sh -lc '
wget -S -O /tmp/assign_response.json --method=POST --header="X-User-Id: 22222222-2222-2222-2222-222222222222" --header="X-Role: CONSULTANT" "http://localhost:8084/api/v1/requests/44444444-4444-4444-4444-444444444444/assign" >/tmp/wget_stdout.log 2>/tmp/wget_stderr.log || true
HTTP_CODE=$(sed -n "s/  HTTP\\/1.1 \\([0-9][0-9][0-9]\\).*/\\1/p" /tmp/wget_stderr.log | tail -n 1)
echo "HTTP_CODE=$HTTP_CODE"
echo "BODY=$(cat /tmp/assign_response.json 2>/dev/null || true)"
'
