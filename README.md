# UMS — University Management System (backend)

Java 21 · Maven 3.9+ · PostgreSQL 18 · modular monolith.

The backend of a university management system for one institution. A JavaFX frontend (built
separately) runs in the same JVM and calls the backend only through the role facades in `ums-api`.
Standing rules for contributors and coding agents: [`CLAUDE.md`](CLAUDE.md). Build spec:
[`docs/prompts/`](docs/prompts). Decisions: [`docs/adr/`](docs/adr). Operations:
[`docs/runbook.md`](docs/runbook.md).

## Module map

| Module | Purpose | Depends on |
| --- | --- | --- |
| `ums-api` | Facade interfaces, DTOs, `ErrorCode`, `UmsApiException`, paging types, annotations — the only module the frontend sees | nothing |
| `ums-platform` | Config, DB (pools, transactions, `BaseRepository`, migrations), interceptor chain, errors and SQL mapping, tracing/metrics/health, state machine, managed executors | `ums-api` |
| `ums-testkit` | Shared test infrastructure: `TestDatabase`, `UmsDbExtension`, `ConcurrencyHarness`, `FailureInjector`, `ArchitectureRules`, `TestData` (test scope only) | `ums-api`, `ums-platform` |
| `ums-core` | Feature packages, facade implementations, Flyway migrations (`db/migration`) | `ums-api`, `ums-platform` |
| `ums-app` | `AppContext` wiring, `UmsRuntime` lifecycle, `Main`; acceptance/E2E tests | all |

## Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL 18 (server for running; binaries for the local test cluster)
- [gitleaks](https://github.com/gitleaks/gitleaks) on `PATH` (optional locally — the pre-commit
  hook warns if missing; CI always scans)

## Database setup

1. Create the databases as a PostgreSQL superuser: `CREATE DATABASE ums;` (and `ums_test` for
   integration tests).
2. Create the roles and schema in each database (idempotent; re-running resets passwords):

   ```
   psql -h localhost -U postgres -d ums -v ON_ERROR_STOP=1 \
        -v owner_pw=... -v app_pw=... -v console_pw=... -v listener_pw=... -v backup_pw=... \
        -f scripts/db/create_roles.sql
   ```

   Roles: `ums_owner` (owns schema, runs migrations), `ums_app`, `ums_dev_console` (read-only,
   15 s statement timeout), `ums_listener` (LISTEN + SELECT on `activity_log`), `ums_backup`
   ([ADR 011](docs/adr/011-database-role-model.md)).
3. Migrations run automatically at startup as `ums_owner`.

## Environment variables

Secrets come from the environment only; non-secret settings come from
`ums-app/src/main/resources/application.properties`, then `application-<env>.properties`, then an
environment variable named `UMS_` + the property after `ums.` (upper case, `.`/`-` → `_`). All
problems are reported together at startup. See [`.env.example`](.env.example).

| Variable | Meaning |
| --- | --- |
| `UMS_ENV` | `LOCAL` (default), `TEST`, `STAGING`, `PROD` |
| `UMS_DB_URL` | JDBC URL, default `jdbc:postgresql://localhost:5432/ums` |
| `UMS_DB_USER` | application login, default `ums_app` |
| `UMS_DB_PASSWORD` | password of `ums_app` |
| `UMS_CONSOLE_DB_PASSWORD` | password of `ums_dev_console` |
| `UMS_LISTENER_DB_PASSWORD` | password of `ums_listener` |
| `UMS_OWNER_DB_PASSWORD` | password of `ums_owner` (migrations) |
| `UMS_BACKUP_DB_PASSWORD` | password of `ums_backup` |
| `UMS_LOG_DIR` | log directory, default `logs` (daily rolling, 14 days, UTC) |
| `UMS_TEST_DB_URL`, `UMS_TEST_DB_USER`, `UMS_TEST_DB_PASSWORD` | integration tests only (admin login of the test database) |

## Git hooks

```
sh scripts/install-hooks.sh     # sets core.hooksPath = scripts/hooks
```

The pre-commit hook runs `mvn -q -Pfast verify` and, if installed, `gitleaks protect --staged`.

## Build

```
mvn -Pfast verify   # format check, compile, unit + property + ArchUnit tests
mvn verify          # full gate: + integration tests, JaCoCo, Checkstyle, SpotBugs, licences
mvn spotless:apply  # format the code
```

Profiles: `fast`, default, `nightly` (concurrency tests × 50), `release` (+ PIT ≥ 70 %, OWASP
Dependency-Check, reads `NVD_API_KEY`). Skipping tests: Failsafe 3.6 no longer honours
`-DskipTests`; use `-DskipTests -DskipITs` (e.g. `mvn install -DskipTests -DskipITs`).

## Run

```
mvn install -DskipTests -DskipITs
mvn -pl ums-app exec:java                       # start headless; Ctrl+C stops cleanly
mvn -pl ums-app exec:java -Dexec.args=--check   # smoke run: start → log health → stop → exit
```

Startup: load config → open pools → migrate → start executors → READY. Health (database,
migrations) is logged after start. Details in the [runbook](docs/runbook.md).

## Running integration tests locally

Integration tests (`*IT`) need a PostgreSQL 18 database prepared by `scripts/db/create_roles.sql`.
They never skip: if a variable is missing they fail with a message naming it. The easiest way is a
throwaway cluster that does not touch any installed PostgreSQL service:

```
# Windows (PowerShell)
powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 init   # once: create + start + ums_test + roles
powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 env    # prints the variables to set
powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 stop   # also: start, status, roles

# Linux / macOS / Git Bash
sh scripts/dev/test-cluster.sh init
eval "$(sh scripts/dev/test-cluster.sh env)"
sh scripts/dev/test-cluster.sh stop
```

The cluster lives in `.tools/pg18-it` (git-ignored), listens on `localhost:55432` only and uses
trust authentication. Binaries are found via `UMS_PG_BIN`, then the default install location
(`C:\Program Files\PostgreSQL\18\bin`, `/usr/pgsql-18/bin`), then `PATH`.

| Variable | Used by | Value for the throwaway cluster |
| --- | --- | --- |
| `UMS_TEST_DB_URL` | all ITs | `jdbc:postgresql://localhost:55432/ums_test` |
| `UMS_TEST_DB_USER` / `UMS_TEST_DB_PASSWORD` | all ITs (admin login; roles exercised via `SET ROLE`) | `postgres` / any |
| `UMS_DB_PASSWORD`, `UMS_CONSOLE_DB_PASSWORD`, `UMS_LISTENER_DB_PASSWORD`, `UMS_OWNER_DB_PASSWORD`, `UMS_BACKUP_DB_PASSWORD` | runtime ITs that log in as the real roles | printed by `env` (throwaway values, trust auth) |

Then run `mvn verify`.

## CI setup

[`.github/workflows/ci.yml`](.github/workflows/ci.yml):

- **push** (all branches): `mvn -B -Pfast verify` and Gitleaks.
- **pull_request**: `postgres:18` service; `ums_test` is created and `scripts/db/create_roles.sql`
  applied with psql inside the container; `mvn -B verify`; reports uploaded; Dependency-Check if
  `NVD_API_KEY` is set.
- **nightly** (21:30 UTC): `mvn -B verify -Pnightly`, Dependency-Check, PIT report (non-blocking
  until P7).

Database login and secrets:

- `UMS_TEST_DB_USER` / `UMS_TEST_DB_PASSWORD` are the container's superuser (`POSTGRES_USER` /
  `POSTGRES_PASSWORD`); integration tests need it for `SET ROLE` and Flyway `clean` in `ums_test`.
- Every role password comes from a GitHub Actions secret and is passed both to
  `create_roles.sql` (`-v owner_pw=… -v app_pw=… -v console_pw=… -v listener_pw=… -v backup_pw=…`)
  and to the tests (`UMS_OWNER_DB_PASSWORD`, `UMS_DB_PASSWORD`, `UMS_CONSOLE_DB_PASSWORD`,
  `UMS_LISTENER_DB_PASSWORD`, `UMS_BACKUP_DB_PASSWORD`).
- Required repository secrets: `UMS_CI_POSTGRES_PASSWORD`, `UMS_CI_OWNER_DB_PASSWORD`,
  `UMS_CI_APP_DB_PASSWORD`, `UMS_CI_CONSOLE_DB_PASSWORD`, `UMS_CI_LISTENER_DB_PASSWORD`,
  `UMS_CI_BACKUP_DB_PASSWORD`; optional `NVD_API_KEY` (Dependency-Check) and, for organisation
  repositories, `GITLEAKS_LICENSE`.
- GitHub masks secret values in logs; the workflow never echoes them and derives no new secret
  values (any future derived value must be masked with `::add-mask::` first).
