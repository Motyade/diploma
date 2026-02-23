-- V3 добавил ESCALATED, но пропустил CANCELED.
-- Этот патч исправляет CHECK-ограничение, добавляя все актуальные статусы.
ALTER TABLE requests DROP CONSTRAINT requests_status_check;

ALTER TABLE requests ADD CONSTRAINT requests_status_check
    CHECK (status IN ('CREATED', 'ASSIGNED', 'COMPLETED', 'ESCALATED', 'CANCELED'));
