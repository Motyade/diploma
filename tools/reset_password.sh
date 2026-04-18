#!/bin/bash
# Reset password for the manager user to "manager123"
# BCrypt hash for "manager123" with cost 10
HASH='$2a$10$7EqJtq98hPqEX7fNZaFWoOHi5uH8Q2K7S6x9Ejo4kKDAdAm8N1sC.'

docker exec retailhub-postgres-1 psql -U retailhub -d auth_db -c \
  "UPDATE credentials SET password_hash = '$HASH' WHERE phone_number = '+70001111111';"

docker exec retailhub-postgres-1 psql -U retailhub -d user_db -c \
  "UPDATE users SET password_hash = '$HASH' WHERE phone_number = '+70001111111';"

echo "Password reset to manager123"
