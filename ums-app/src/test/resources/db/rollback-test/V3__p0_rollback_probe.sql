-- Test-only migration (RollbackCompatibilityIT): simulates "version N" of the schema.
-- Lives outside classpath:db/migration, so the application build under test (N-1) does not know it.
CREATE TABLE rollback_probe (
  id         BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_rollback_probe PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
