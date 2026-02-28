-- Снимаем ограничение NOT NULL с users.store_id.
-- Нужно для сценария: менеджер регистрируется до создания магазина (POST /stores).
ALTER TABLE users ALTER COLUMN store_id DROP NOT NULL;
