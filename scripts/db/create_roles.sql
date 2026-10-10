-- UMS database roles and schema (P0 Step 7). Idempotent: safe to re-run; re-running resets passwords.
--
-- Run as a PostgreSQL superuser, connected to the target database (ums or ums_test), which must
-- already exist:
--
--   psql -h localhost -U postgres -d ums -v ON_ERROR_STOP=1 \
--        -v owner_pw=... -v app_pw=... -v console_pw=... -v listener_pw=... -v backup_pw=... \
--        -f scripts/db/create_roles.sql
--
-- Passwords come only from psql variables; nothing is hard-coded.
--
-- Roles (cluster-wide):
--   ums_owner        owns the database and schema ums; Flyway migrations run as this role
--   ums_app          the application (DML on all tables, sequences)
--   ums_dev_console  dev query console: read-only by default, statement_timeout 15s
--   ums_listener     dedicated LISTEN connection: no default privileges; V2 grants USAGE on ums and
--                    SELECT on ums.activity_log only
--   ums_backup       pg_dump: read-only

\set ON_ERROR_STOP on

\if :{?owner_pw}
\else
  DO $$ BEGIN RAISE EXCEPTION 'create_roles.sql: psql variable owner_pw is required'; END $$;
\endif
\if :{?app_pw}
\else
  DO $$ BEGIN RAISE EXCEPTION 'create_roles.sql: psql variable app_pw is required'; END $$;
\endif
\if :{?console_pw}
\else
  DO $$ BEGIN RAISE EXCEPTION 'create_roles.sql: psql variable console_pw is required'; END $$;
\endif
\if :{?listener_pw}
\else
  DO $$ BEGIN RAISE EXCEPTION 'create_roles.sql: psql variable listener_pw is required'; END $$;
\endif
\if :{?backup_pw}
\else
  DO $$ BEGIN RAISE EXCEPTION 'create_roles.sql: psql variable backup_pw is required'; END $$;
\endif

-- psql variables are not expanded inside DO bodies; pass them through session-local settings.
SELECT set_config('ums.owner_pw',   :'owner_pw',   false) IS NOT NULL
   AND set_config('ums.app_pw',     :'app_pw',     false) IS NOT NULL
   AND set_config('ums.console_pw', :'console_pw', false) IS NOT NULL
   AND set_config('ums.listener_pw', :'listener_pw', false) IS NOT NULL
   AND set_config('ums.backup_pw',  :'backup_pw',  false) IS NOT NULL AS passwords_staged \gset

-- 1. Roles ----------------------------------------------------------------------------------
DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT * FROM (VALUES ('ums_owner',       'ums.owner_pw'),
                          ('ums_app',         'ums.app_pw'),
                          ('ums_dev_console', 'ums.console_pw'),
                          ('ums_listener',    'ums.listener_pw'),
                          ('ums_backup',      'ums.backup_pw')) AS t(role_name, setting)
  LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r.role_name) THEN
      EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', r.role_name, current_setting(r.setting));
    ELSE
      EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', r.role_name, current_setting(r.setting));
    END IF;
    EXECUTE format('ALTER ROLE %I NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
                   r.role_name);
  END LOOP;
END
$$;

SELECT set_config('ums.owner_pw', '', false) = ''
   AND set_config('ums.app_pw', '', false) = ''
   AND set_config('ums.console_pw', '', false) = ''
   AND set_config('ums.listener_pw', '', false) = ''
   AND set_config('ums.backup_pw', '', false) = '' AS passwords_cleared \gset

ALTER ROLE ums_dev_console SET statement_timeout = '15s';

-- 2. Database --------------------------------------------------------------------------------
DO $$
BEGIN
  EXECUTE format('ALTER DATABASE %I OWNER TO ums_owner', current_database());
  EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO ums_app, ums_dev_console, ums_listener, ums_backup',
                 current_database());
END
$$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- 3. Schema ----------------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS ums AUTHORIZATION ums_owner;
ALTER SCHEMA ums OWNER TO ums_owner;
REVOKE ALL ON SCHEMA ums FROM PUBLIC;
GRANT USAGE ON SCHEMA ums TO ums_app, ums_dev_console, ums_backup;

-- 4. Privileges on objects ums_owner creates from now on --------------------------------------
ALTER DEFAULT PRIVILEGES FOR ROLE ums_owner IN SCHEMA ums
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ums_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ums_owner IN SCHEMA ums
  GRANT USAGE, SELECT ON SEQUENCES TO ums_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ums_owner IN SCHEMA ums
  GRANT SELECT ON TABLES TO ums_dev_console, ums_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE ums_owner IN SCHEMA ums
  GRANT SELECT ON SEQUENCES TO ums_backup;

-- 5. Same privileges on objects that already exist (re-run after migrations) -----------------
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ums TO ums_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ums TO ums_app;
GRANT SELECT ON ALL TABLES IN SCHEMA ums TO ums_dev_console, ums_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA ums TO ums_backup;
DO $$
BEGIN
  IF to_regclass('ums.flyway_schema_history') IS NOT NULL THEN
    REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ums.flyway_schema_history FROM ums_app;
  END IF;
END
$$;
