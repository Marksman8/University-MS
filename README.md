# UMS — University Management System (backend)

Java 21 · Maven 3.9+ · PostgreSQL 16 · modular monolith.

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
