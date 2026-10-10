# Architecture Decision Records

Each ADR has Status, Context, Decision, Consequences and Alternatives. ADRs 001–009 follow the
decision list of DESIGN.md (written from master prompt §4–§12 and the STOP A–C decisions while
`docs/design/DESIGN.md` is not yet in the repository); 010–012 record decisions made during P0.

| ADR | Title | Status |
| --- | --- | --- |
| [001](001-modular-monolith-in-process-facades.md) | Modular monolith with in-process facades | Accepted |
| [002](002-five-maven-modules.md) | Five Maven modules; the frontend depends only on `ums-api` | Accepted |
| [003](003-interceptor-chain-jdk-proxies.md) | Interceptor chain over JDK dynamic proxies | Accepted |
| [004](004-plain-jdbc-no-orm.md) | Plain JDBC, no ORM | Accepted |
| [005](005-integrity-rules-as-db-constraints.md) | Integrity rules as database constraints | Accepted |
| [006](006-concurrency-model.md) | READ COMMITTED, conditional updates, lock order, optimistic versions, retry (3 attempts in total) | Accepted |
| [007](007-generic-state-machine-cas.md) | Generic StateMachine with compare-and-set persistence | Accepted |
| [008](008-async-activity-log-sync-change-history.md) | Asynchronous activity log plus synchronous change history | Accepted |
| [009](009-keyset-paging-only.md) | Keyset paging only | Accepted |
| [010](010-postgresql-18-single-target.md) | PostgreSQL 18 as the single target | Accepted |
| [011](011-database-role-model.md) | Database role model (least privilege, including `ums_listener`) | Accepted |
| [012](012-explicit-constraint-index-naming.md) | Explicit constraint and index names as a contract for `SqlErrorMapper` | Accepted |
