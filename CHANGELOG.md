# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Multi-module Maven build (`ums-api`, `ums-platform`, `ums-testkit`, `ums-core`, `ums-app`) with pinned dependency and plugin versions and enforcer rules.
- Quality gates: Spotless (google-java-format), Checkstyle design rules, SpotBugs + FindSecBugs, JaCoCo line-coverage check (≥ 80 % on platform/service/domain), third-party licence allow-list, Surefire (`*Test`) / Failsafe (`*IT`) split.
- Build profiles `fast`, default, `nightly`, `release` (PIT mutation ≥ 70 %, OWASP Dependency-Check CVSS ≥ 7).
- Versioned pre-commit hook (`scripts/hooks/pre-commit`) running the fast gate and gitleaks; `scripts/install-hooks.sh`.
- `ums-api` foundations: `ErrorCode` (34 codes with `error.<area>.<lower_snake>` keys and English messages), `UmsApiException`, `Page`/`Cursor`/`PageRequest` (size 1..200), `EntityId`, `Role`, `UserSummary`, `Subscription`, `UmsClient`, `Session`, empty role facades, `NoticeListener`, annotations `@Writes @Audited @Feature @RateLimited`.
- Typed configuration: `Environment` (from `UMS_ENV`), `Secret` (masked, zeroable), immutable `AppConfig`, `ConfigLoader` (base → `application-<env>` → environment variables; secrets from the environment only; all problems reported together in one `ConfigException`); default `application.properties`.
- Logging: `logback.xml` with console + daily rolling file (14 days), UTC timestamps and `traceId`/`userId`/`role` MDC fields.
- Exception hierarchy under `UmsException` (domain, security, policy, infrastructure branches), `IncidentId`, `ConstraintErrorMap`, `SqlErrorMapper` (SQLState/constraint → error code) and `ApiErrorTranslator` (→ `UmsApiException`, safe arguments only, incident ids for internal errors).
- Database access: HikariCP `ums-app` / `ums-console` pools and a dedicated listener connection (schema `ums`, statement/lock timeouts, `logServerErrorDetail=false`), `TransactionRunner` (READ COMMITTED, thread-bound, join semantics, conflict retry at the outermost transaction), `RetryPolicy`, `BaseRepository` templates (`queryOne/Optional/List`, keyset `queryPage`, `update`, `updateExactlyOne`, `batch`, `casState`), `CursorCodec`.
- Flyway baseline `V1__p0_baseline.sql` (schema `ums`, `btree_gist`, `app_error_log`, `feature_flag` with five enabled flags, `backup_log`, `activity_log` with NOTIFY trigger, `change_history`; every constraint and index explicitly named), `MigrationRunner` (as `ums_owner`, validate on migrate, clean only in TEST), idempotent `scripts/db/create_roles.sql` (`ums_owner`, `ums_app`, `ums_dev_console`, `ums_backup`, default privileges, console `statement_timeout` 15 s).
- Dedicated least-privilege `ums_listener` role (CONNECT only from the roles script; `V2__p0_listener_grants.sql` grants USAGE on `ums` and SELECT on `activity_log`); the listener connection logs in as `ums_listener`.
- Facade interceptor framework: `FacadeProxyFactory` (JDK proxy, chain sorted by `InterceptorSlot`, duplicate slots rejected, Object methods bypass), re-entrant `Invocation`, `ErrorMappingInterceptor`, `TracingInterceptor` (`Tracer`/`TraceContext`, MDC `traceId`, `MetricsRegistry`), `TransactionInterceptor` (delegates to `TransactionRunner`; no second retry layer).
- Generic `StateMachine<S, C>` (builder, `EnumMap` transition table, `Guard`/`GuardResult`/`Action`, `fire`, `allowedFrom`).
- Managed concurrency: `NamedThreadFactory` (uncaught failures → log + `UncaughtErrorSink`), `ManagedExecutors` (bounded fixed pools with `CALLER_RUNS` or `BUSY_TRY_AGAIN` rejection, schedulers, staged shutdown ACCEPT_STOP → DRAINERS → SCHEDULER → LISTENERS → POOLS), `Scheduler` (jobs survive exceptions, ≤ 32 jobs), lock-free `BoundedRing`.
- Runtime: `UmsRuntime` (config → pools → migrate → executors → ready; staged stop; idempotent), `AppContext` composition root, `Main` (`exec:java`, `--check` smoke run), `HealthService` with `DatabaseReachableProbe` and `MigrationsCurrentProbe` (2 s per probe, UP/DEGRADED/DOWN), `Readiness` gate (`DB_UNAVAILABLE` until ready).
