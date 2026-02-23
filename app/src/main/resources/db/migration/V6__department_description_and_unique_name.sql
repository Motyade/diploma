-- 1. Добавляем поле description в departments (опциональное)
ALTER TABLE departments ADD COLUMN description TEXT;

-- 2. Добавляем уникальность name в пределах одного магазина
--    (два разных магазина могут иметь отдел с одинаковым именем)
ALTER TABLE departments ADD CONSTRAINT uq_departments_store_name UNIQUE (store_id, name);
