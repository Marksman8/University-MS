# ADR 011 — Database role model (least privilege, including `ums_listener`)

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §6, §11; P0 Step 7; STOP B rulings 2 and 3

## Context

One application process touches the database for different purposes: schema migration, normal
work, a developer query console, a LISTEN connection and backups. A single all-powerful login
would turn any bug or console query into a schema-level risk.

## Decision

Five login roles, created idempotently by `scripts/db/create_roles.sql` (passwords from psql
variables only), all `NOSUPERUSER NOCREATEDB NOCREATEROLE`:

| Role | Purpose | Privileges |
| --- | --- | --- |
| `ums_owner` | Flyway migrations | owns the database and schema `ums` |
| `ums_app` | the application | DML on all tables, sequences; read-only on `flyway_schema_history` |
| `ums_dev_console` | Dev query console | SELECT only by default; `statement_timeout = 15s`; no `password_hash` (P1) |
| `ums_listener` | dedicated LISTEN connection | CONNECT, USAGE on `ums`, SELECT on `activity_log` only (`V2__p0_listener_grants.sql`) |
| `ums_backup` | `pg_dump` | SELECT on tables and sequences |

`REVOKE ALL ON DATABASE … FROM PUBLIC`; default privileges `FOR ROLE ums_owner` cover tables
created later. The roles script owns schema creation; Flyway runs with `createSchemas(false)`.
Integration tests use one admin login plus `SET ROLE`, so role passwords never appear in test
configuration; CI passes real role passwords from masked secrets for runtime tests.

## Consequences

- A console or listener bug cannot change data or schema; the application cannot alter the
  schema or migration history.
- Every new table automatically gets the right grants; new roles need an ADR update.
- ITs prove the boundaries (`DatabaseRolesIT`, `ListenerRoleIT`).

## Alternatives

- **One application login** — maximal blast radius.
- **Listener reusing `ums_app`** — rejected at STOP B (more privilege than LISTEN needs).
