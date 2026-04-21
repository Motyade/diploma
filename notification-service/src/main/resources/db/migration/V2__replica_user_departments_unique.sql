-- Удаляем возможные существующие дубликаты (оставляя одну строку на пару user_id, department_id)
DELETE FROM replica_user_departments a
USING replica_user_departments b
WHERE a.id > b.id
  AND a.user_id = b.user_id
  AND a.department_id = b.department_id;

ALTER TABLE replica_user_departments
    ADD CONSTRAINT uk_replica_user_departments_user_dept UNIQUE (user_id, department_id);
