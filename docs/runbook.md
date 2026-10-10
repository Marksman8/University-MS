# UMS Runbook

Operational procedures for what exists after P0. Sections marked **P6** are filled in when backup,
alerts and incident tooling are built.

## Start and stop

**Prerequisites:** PostgreSQL 18 reachable; databases and roles created with
`scripts/db/create_roles.sql`; environment variables set (README → Environment variables).

**Start**

```
mvn install -DskipTests -DskipITs
mvn -pl ums-app exec:java
```

Startup order: load and validate configuration → open the `ums-app` and `ums-console` pools →
migrate as `ums_owner` → start executors (`scheduler`, `health-probe`) → mark READY. Expected log
lines (UTC, with `traceId`/`userId`/`role` MDC fields):

```
UMS starting (environment PROD)
UMS ready (0 migration(s) applied)
Health UP: [ProbeResult[name=database, status=UP, ...], ProbeResult[name=migrations, status=UP, ...]]
```

**Smoke check** (start → health → stop → exit, exit code 0 on success):

```
mvn -pl ums-app exec:java -Dexec.args=--check
```

**Stop:** Ctrl+C (or SIGTERM). The shutdown hook runs the staged sequence, each stage within 5 s:

```
UMS stopped: [ACCEPT_STOP:readiness, SCHEDULER:scheduler, POOLS:health-probe, POOLS:connection-pools]
```

(Order in general: ACCEPT_STOP → DRAINERS → SCHEDULER → LISTENERS → POOLS.) `stop` and `start` are
idempotent.

**Startup failures (fail fast, nothing left open)**

| Symptom | Meaning | Action |
| --- | --- | --- |
| `Invalid configuration:` followed by a list | missing/invalid keys or secrets (all listed at once) | set the listed variables; secrets come from the environment only |
| `UMS cannot start: database unavailable…` within a few seconds | PostgreSQL unreachable at `UMS_DB_URL` | check the server, port, firewall and URL |
| Flyway validation error (`INTERNAL_ERROR`) | a migration on disk differs from the applied one | never edit applied migrations; restore the original file |

## Health

Two probes run in parallel on the `health-probe` pool, 2 s each; a slow, throwing or
unschedulable probe is DOWN. Overall status is the worst probe.

| Probe | UP | DEGRADED | DOWN |
| --- | --- | --- | --- |
| `database` | `SELECT 1` succeeds (detail: latency) | — | unreachable (detail: SQLState) |
| `migrations` | `schema current`, or `schema ahead of application (rollback mode)` | `n migration(s) pending` | migration state unreadable |

Readiness: facade calls fail with `DB_UNAVAILABLE` until startup completes and again from the first
shutdown stage. Health is logged at startup and by `--check`; a Dev health view and alerts arrive
in P6.

## Backup

**P6** — daily `pg_dump -Fc` as `ums_backup`, verification with `pg_restore --list`, 30-day
retention, weekly copy to the second path.

## Restore

**P6**

## Incidents

**P6** — alerts (error rate, probe DOWN, backup missed, audit drops) and incident handling. Until
then: an `INTERNAL_ERROR` shown to a user carries an 8-character incident id; search the log for
`incident=<id>` to find the ERROR line with the stack trace.

## Rollback

Rollback means running **version N-1 of the application on version N's schema**. The schema is
never rolled back.

Why it works:

- Migrations are expand-then-contract: a release only adds; removals ship one release later, so
  version N's schema still contains everything N-1 uses.
- `MigrationRunner` sets Flyway `ignoreMigrationPatterns("*:future")` explicitly: applied
  migrations newer than the running build are ignored, not fatal. Validation still fails for
  changed or missing *known* migrations.
- Proven by `RollbackCompatibilityIT` (schema with V1–V3, application with V1–V2 → READY, UP).

Procedure:

1. Stop the current version (Ctrl+C / SIGTERM) and confirm `UMS stopped: […]` in the log.
2. Deploy the N-1 build (same database, same environment variables).
3. Run `mvn -pl ums-app exec:java -Dexec.args=--check` (or start normally) and confirm
   `UMS ready (0 migration(s) applied)` and the `migrations` probe detail
   **`schema ahead of application (rollback mode)`** with overall `Health UP`.
4. Start normally. Re-deploy version N later; it finds its migrations already applied.

Never run Flyway `clean` outside `TEST` (it is disabled there by configuration) and never delete
rows from `flyway_schema_history`.
