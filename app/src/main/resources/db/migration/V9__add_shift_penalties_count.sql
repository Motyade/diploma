-- Поле penalties_count уже объявлено в сущности Shift.java,
-- но отсутствовало в исходной схеме БД (V1).
-- Добавляем колонку с дефолтом 0 — существующие строки не затрагиваются.
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS penalties_count INT NOT NULL DEFAULT 0;
