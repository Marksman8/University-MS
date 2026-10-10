-- Least-privilege grants for the dedicated LISTEN connection (STOP B ruling 2).
-- ums_listener is created by scripts/db/create_roles.sql with no default privileges.
GRANT USAGE ON SCHEMA ums TO ums_listener;
GRANT SELECT ON ums.activity_log TO ums_listener;
