-- P0 baseline. Runs as ums_owner. Schema ums and role grants come from scripts/db/create_roles.sql.
-- Naming rule: every constraint and index is named explicitly (pk_, fk_, uq_, ck_, ex_, idx_).

CREATE SCHEMA IF NOT EXISTS ums;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Flyway's history table is owned by ums_owner; the application may read it but never change it.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM ums_app;

-- ---------------------------------------------------------------------------------------------
-- Errors captured by the application (shown in the Dev diagnostics).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE app_error_log (
  id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_app_error_log PRIMARY KEY,
  occurred_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
  level       VARCHAR(8)   NOT NULL,
  source      VARCHAR(128) NOT NULL,
  message     TEXT         NOT NULL,
  stack_trace TEXT,
  trace_id    UUID,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_error_log_occurred_at ON app_error_log (occurred_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Feature flags (master prompt §12). updated_by gets its FK to app_user in P1.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE feature_flag (
  key        TEXT        NOT NULL CONSTRAINT pk_feature_flag PRIMARY KEY,
  enabled    BOOLEAN     NOT NULL,
  updated_by BIGINT,
  updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO feature_flag (key, enabled) VALUES
  ('REGISTRATION',   TRUE),
  ('MARKS_ENTRY',    TRUE),
  ('RESULT_PUBLISH', TRUE),
  ('NOTICES',        TRUE),
  ('PRIVACY_EXPORT', TRUE);

-- ---------------------------------------------------------------------------------------------
-- Backup runs (one row per run; RUNNING until it finishes).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE backup_log (
  id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_backup_log PRIMARY KEY,
  started_at  TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,
  file_path   TEXT,
  size_bytes  BIGINT,
  verified    BOOLEAN     NOT NULL DEFAULT FALSE,
  outcome     VARCHAR(16) NOT NULL
              CONSTRAINT ck_backup_log_outcome CHECK (outcome IN ('RUNNING','SUCCESS','FAILURE')),
  message     TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_backup_log_started_at ON backup_log (started_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Activity log (pinned in P0 Step 7). Columns, types, checks, indexes and trigger exactly as
-- specified; the three previously unnamed constraints are named per the naming rule.
-- user_id gets its FK to app_user in P1.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE activity_log (
  id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_activity_log PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  user_id     BIGINT,                        -- FK to app_user added in P1
  username    VARCHAR(64),
  role        VARCHAR(16) NOT NULL CONSTRAINT ck_activity_log_role
              CHECK (role IN ('STUDENT','FACULTY','ADMIN','DEV','SYSTEM','ANONYMOUS')),
  session_id  UUID,
  action      VARCHAR(64) NOT NULL,
  entity_type VARCHAR(64),
  entity_id   VARCHAR(64),
  outcome     VARCHAR(8)  NOT NULL CONSTRAINT ck_activity_log_outcome
              CHECK (outcome IN ('SUCCESS','FAILURE')),
  details     JSONB,
  trace_id    UUID
);
CREATE INDEX idx_activity_time      ON activity_log (occurred_at DESC, id DESC);
CREATE INDEX idx_activity_role_time ON activity_log (role, occurred_at DESC, id DESC);
CREATE INDEX idx_activity_user_time ON activity_log (user_id, occurred_at DESC, id DESC);
CREATE FUNCTION notify_activity() RETURNS trigger AS $$
BEGIN PERFORM pg_notify('ums_activity', NEW.id::text); RETURN NEW; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_activity_notify AFTER INSERT ON activity_log
  FOR EACH ROW EXECUTE FUNCTION notify_activity();

-- ---------------------------------------------------------------------------------------------
-- Change history (before/after), written synchronously in the same transaction as the change.
-- actor_id gets its FK to app_user in P1.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE change_history (
  id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_change_history PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_id    BIGINT,
  entity_type VARCHAR(64) NOT NULL,
  entity_id   VARCHAR(64) NOT NULL,
  before      JSONB,
  after       JSONB,
  reason      TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_change_history_entity ON change_history (entity_type, entity_id, occurred_at DESC);
