ALTER TABLE requests DROP CONSTRAINT requests_status_check;

ALTER TABLE requests ADD CONSTRAINT requests_status_check 
    CHECK (status IN ('CREATED', 'ASSIGNED', 'COMPLETED', 'ESCALATED'));

ALTER TABLE shifts ADD COLUMN penalties_count INT DEFAULT 0;
