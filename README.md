# UMS — University Management System (backend)

Java 21 · Maven 3.9+ · PostgreSQL 18 · modular monolith.

> Skeleton. Full setup, run and test instructions arrive in P0 Step 14.

## Modules

| Module | Purpose |
| --- | --- |
| `ums-api` | Facade interfaces, DTOs, error codes — the only module the frontend sees |
| `ums-platform` | Config, DB, interceptors, security primitives, errors, audit/observe infrastructure |
| `ums-testkit` | Shared test infrastructure (test scope only) |
| `ums-core` | Feature packages, facade implementations, Flyway migrations |
| `ums-app` | Wiring (`AppContext`), `Main`, startup/shutdown |

## Build

```
mvn -Pfast verify   # quick: format, compile, unit tests
mvn verify          # full gate
```

See `CLAUDE.md` for standing rules and `docs/prompts/` for the build spec.

## Running integration tests locally

Integration tests (`*IT`) need a PostgreSQL 18 database prepared by `scripts/db/create_roles.sql`.
They never skip: if the variables below are missing they fail with a message saying which one.
The easiest way is a throwaway cluster that does not touch any installed PostgreSQL service:

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
