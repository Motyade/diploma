\connect user_db
SELECT count(*) AS users_count FROM users;
SELECT id, phone_number, role FROM users;

\connect request_db
SELECT count(*) AS replica_users_count FROM replica_users;
SELECT id, role, current_status FROM replica_users;

\connect notification_db
SELECT count(*) AS replica_users_count FROM replica_users;
SELECT id, role, current_status FROM replica_users;

\connect analytics_db
SELECT count(*) AS dim_users_count FROM dim_users;
SELECT id, role FROM dim_users;

\connect auth_db
SELECT count(*) AS credentials_count FROM credentials;
SELECT user_id, phone_number, role FROM credentials;
