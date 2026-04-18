#!/bin/bash
set -e
cd /opt/retailhub

echo "=== Building changed services ==="

echo ""
echo "--- Building shared event-contracts ---"
cd /opt/retailhub/shared/event-contracts
mvn clean install -q -DskipTests 2>&1 | tail -3

echo ""
echo "--- Building analytics-service ---"
cd /opt/retailhub/analytics-service
mvn clean package -q -DskipTests 2>&1 | tail -3

echo ""
echo "--- Building request-service ---"
cd /opt/retailhub/request-service
mvn clean package -q -DskipTests 2>&1 | tail -3

echo ""
echo "--- Building user-service ---"
cd /opt/retailhub/user-service
mvn clean package -q -DskipTests 2>&1 | tail -3

echo ""
echo "--- Building store-service ---"
cd /opt/retailhub/store-service
mvn clean package -q -DskipTests 2>&1 | tail -3

echo ""
echo "--- Building api-gateway ---"
cd /opt/retailhub/api-gateway
mvn clean package -q -DskipTests 2>&1 | tail -3

echo ""
echo "=== Rebuilding and restarting Docker containers ==="
cd /opt/retailhub

docker compose build analytics-service request-service user-service store-service api-gateway 2>&1 | tail -20
docker compose up -d analytics-service request-service user-service store-service api-gateway 2>&1 | tail -10

echo ""
echo "=== Waiting for services to start (60s) ==="
sleep 60

echo ""
echo "=== Checking service health ==="
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep retailhub

echo ""
echo "=== DEPLOY DONE ==="
