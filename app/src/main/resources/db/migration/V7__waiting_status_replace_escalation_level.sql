-- Рефакторинг эскалации: заменяем escalation_level на полноценный статус WAITING.
-- Преимущество: состояние хранится в одном поле status, не нужна лишняя колонка.

-- Удаляем колонку escalation_level (4 байта на строку, теперь не нужна)
ALTER TABLE requests DROP COLUMN IF EXISTS escalation_level;

-- Добавляем WAITING в список допустимых значений статуса
-- В PostgreSQL нельзя изменить CHECK constraint напрямую — удаляем и создаём заново
ALTER TABLE requests DROP CONSTRAINT IF EXISTS requests_status_check;
ALTER TABLE requests ADD CONSTRAINT requests_status_check
    CHECK (status IN ('CREATED', 'WAITING', 'ESCALATED', 'ASSIGNED', 'COMPLETED', 'CANCELED'));
