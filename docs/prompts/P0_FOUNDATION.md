# P0 — Foundation (time-box: 1 week)

> Place at `docs/prompts/P0_FOUNDATION.md`. Read `CLAUDE.md` and `docs/prompts/00_MASTER_PROMPT.md` first.
> Branches: `phase-0/scaffold`, `phase-0/platform`, `phase-0/ci-docs`. One commit per step (Conventional Commits).
> **Exit gate:** the empty application builds, migrates a blank database, starts and stops cleanly, and passes every check locally and in CI.

No business features in P0. You are building the skeleton every later phase depends on. There are **four STOP points**; after each, write the STOP report (CLAUDE.md §8) and wait.

---

## Step 1 — Multi-module scaffold · `chore: scaffold multi-module maven build`

- Root `pom.xml` (`packaging pom`), groupId `edu.ums`, project version `0.1.0-SNAPSHOT` until the first release (the no-SNAPSHOT rule applies to dependencies, not to the project's own version).
- Modules in this reactor order: `ums-api`, `ums-platform`, `ums-testkit`, `ums-core`, `ums-app`.
  - `ums-testkit` depends on `ums-api` + `ums-platform` only.
  - `ums-core` depends on `ums-api`, `ums-platform`; test scope on `ums-testkit`.
  - `ums-app` depends on all; test scope on `ums-testkit`. Acceptance/E2E tests live here.
- Properties: `maven.compiler.release=21`, `project.build.sourceEncoding=UTF-8`, one property per pinned dependency version.
- `dependencyManagement` + `pluginManagement` for every approved artifact (master §3), exact versions verified on Maven Central.
- `maven-enforcer-plugin`: `requireJavaVersion [21,)`, `requireMavenVersion [3.9,)`, `dependencyConvergence`, `requireUpperBoundDeps`, `banDuplicatePomDependencyVersions`, `bannedDependencies` excluding `org.springframework*`, `org.hibernate*`, `org.projectlombok*`, `com.google.guava*`.
- Repo files: `.gitignore` (target/, .idea/, *.iml, .env, logs/, backups/), `.gitattributes` (`* text=auto eol=lf`), `.editorconfig`, `.env.example` (every env var from master §11, blank values), `README.md` (skeleton), `CHANGELOG.md` (Keep a Changelog, `## [Unreleased]`), `BACKLOG.md`, `docs/adr/` (empty for now), `docs/runbook.md` (headings only).

## Step 2 — Quality gates · `build: add formatting, static analysis, coverage and licence checks`

- Spotless with google-java-format (check in build, apply via `mvn spotless:apply`).
- Checkstyle `config/checkstyle/checkstyle.xml`: no star imports, `MethodLength` max 60 (error), `ParameterNumber` max 7, no `System.out`/`printStackTrace`, `IllegalCatch` (Exception/Throwable/RuntimeException) with suppressions only for the three allowed catch sites (marked `// CHECKSTYLE:ALLOW-CATCH <reason>`), `FinalClass`, `HideUtilityClassConstructor`.
- SpotBugs + FindSecBugs, threshold medium, fail on high; `config/spotbugs/exclude.xml` empty to start.
- JaCoCo: report per module; check rule ≥ 0.80 line coverage on `edu.ums.platform.*`, `*.service.*`, `*.domain.*`.
- Surefire runs `*Test` (JUnit 5 + jqwik engines); Failsafe runs `*IT`.
- `license-maven-plugin` third-party report; fail if any licence is outside: Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, EPL-1.0, EPL-2.0, LGPL-2.1, PostgreSQL, ISC, EUPL-1.1, GPL-2.0-with-classpath-exception.
- Profiles:
  - `fast` — Spotless check, compile, Surefire, ArchUnit (skips Failsafe, SpotBugs, JaCoCo check).
  - default — everything except PIT and Dependency-Check.
  - `nightly` — default + `-Dums.concurrency.repeats=50`.
  - `release` — default + PIT (mutation ≥ 70 % on service/domain) + OWASP Dependency-Check (fail CVSS ≥ 7; reads `NVD_API_KEY` env var if present).
- Git hook: `scripts/hooks/pre-commit` (POSIX sh, works in Git Bash on Windows) runs `mvn -q -Pfast verify` and `gitleaks protect --staged --redact` if `gitleaks` is on PATH (else prints a warning; CI enforces). `scripts/install-hooks.sh` runs `git config core.hooksPath scripts/hooks`.

### STOP A — "Build skeleton"
Show: `mvn -Pfast verify` and `mvn verify` output (both green on empty modules), enforcer passing, licence report summary, hook blocking a deliberately mis-formatted file (then revert). Await review.

---

## Step 3 — `ums-api` foundations · `feat(api): error codes, exceptions, paging and session contracts`

Package `edu.ums.api`:
- `error/ErrorCode` enum with **exactly** the codes in master §10; each has `messageKey()` = `error.<area>.<lower_snake>` (area: auth, policy, domain, offer, reg, att, marks, infra).
- `error/UmsApiException extends RuntimeException` — fields `ErrorCode code`, `String messageKey`, `Map<String,Object> args` (unmodifiable copy), optional `String incidentId`; no SQL or stack details in `getMessage()`.
- `id/EntityId` sealed? No — plain interface `EntityId { long value(); }`; concrete ids arrive with their features.
- `dto/Page<T>(List<T> items, Optional<Cursor> next)` (compact constructor copies list, checks non-null); `dto/Cursor(String token)` (non-blank, ≤ 512 chars); `dto/PageRequest(Optional<Cursor> after, int size)` validating 1..200.
- `Role` enum `STUDENT FACULTY ADMIN DEV`; `UserSummary` record; `Subscription` (`unsubscribe()`); `UmsClient`; `Session` exactly as master §5. Facade interfaces `StudentApi`, `FacultyApi`, `AdminApi`, `DevApi` exist with class Javadoc and **no methods yet**.
- `annotation/Writes`, `Audited(String action)`, `Feature(String flag)`, `RateLimited(int perMinute)` — `@Retention(RUNTIME) @Target(METHOD)`.
- `src/main/resources/i18n/messages_en.properties` with a clear English sentence for every code.
- Tests: every `ErrorCode` has a non-blank message; keys unique and well-formed; `PageRequest` bounds (jqwik); `UmsApiException` args immutable.

## Step 4 — Configuration and logging · `feat(platform): typed configuration and structured logging`

- `config/Environment` enum `LOCAL TEST STAGING PROD` from `UMS_ENV`.
- `config/Secret` — wraps `char[]`, `toString()` returns `****`, `clear()` zeroes it.
- `config/AppConfig` immutable record: DB URL, app/console/listener/owner/backup users + `Secret` passwords, pool sizes, timeouts, defaults (credit limit 26, attendance 75, edit window 48 h, lockout 5/15 min, idle 30 min), backup dir + second dir, log dir.
- `config/ConfigLoader` — loads `application.properties`, then `application-<env>.properties`, then env-var overrides; validates everything at once and throws `ConfigException` listing **all** missing/invalid keys; never logs secrets.
- `logback.xml` in `ums-app`: console + daily rolling file 14 days, UTC timestamps, pattern includes `%X{traceId} %X{userId} %X{role}`.
- Tests: missing vars listed together; secrets masked in `toString`; override precedence (property-based over random key sets).

## Step 5 — Error hierarchy and SQL mapping · `feat(platform): exception hierarchy and SQL error mapping`

- `error/` classes exactly as master §10 (abstract `UmsException(ErrorCode, Map details, Throwable cause)`).
- `IncidentId` — 8-char random Base32, generated per INTERNAL_ERROR.
- `ConstraintErrorMap` — immutable `Map<String, ErrorCode>` built once; register all constraint names from master §6/§10 now (tables arrive later; names are contracts).
- `SqlErrorMapper.map(SQLException, boolean cancelRequested)` → `UmsException` per master §10 table; walks `getNextException()`/cause for the SQLState; extracts constraint name from `PSQLException.getServerErrorMessage()`.
- `ApiErrorTranslator` → `UmsApiException` (copies code, key, safe args; INTERNAL_ERROR gets incident id; logs per severity rules).
- Complexity headers on mapper (O(1) lookup).
- Tests: one per SQLState row; unknown state → INTERNAL_ERROR; jqwik: mapper never returns null and never leaks SQL text into args.

## Step 6 — Database access · `feat(platform): pools, transactions, base repository, keyset paging`

- `db/DataSources` — HikariCP `app` (max 10, connectionTimeout 5 000 ms) and `console` (max 3); `ListenerConnectionFactory` for one dedicated connection. `connectionInitSql`: `SET search_path = ums; SET statement_timeout = '10s'; SET lock_timeout = '5s'` (console: `15s`). Pool names exposed for metrics.
- `db/Tx` (connection + readOnly flag); `db/TransactionRunner` with a `ThreadLocal<Tx>`: `inTransaction(boolean readOnly, Supplier<T>)` — joins an existing tx (REQUIRED), else opens one at READ COMMITTED, commits, rolls back on any throwable, always restores the ThreadLocal.
- `db/RetryPolicy` — 3 attempts in total (2 retries), backoff uniform 20–100 ms from an injected `RandomGenerator`; retries only `ConflictException`-class failures (40001, 40P01) and only at the outermost transaction.
- `db/BaseRepository` (abstract, template methods): `queryOne`, `queryOptional`, `queryList` (bounded by a required `limit`), `queryPage(KeysetQuery, PageRequest, RowMapper)`, `update`, `updateExactlyOne` (0 rows → `StaleDataException`), `batch`, `casState(table, id, fromState, toState, version)`. All `PreparedStatement`, all try-with-resources, all SQLExceptions through `SqlErrorMapper`.
- `db/CursorCodec` — encodes `(sortKey..., id)` as Base64 JSON; decode validates shape; tampered cursor → `VALIDATION_FAILED`.
- Tests (`*IT` against PostgreSQL): commit/rollback, nested joins outer tx, read-only tx rejects writes, retry on injected 40001 (fake connection wrapper in a unit test), statement timeout → TIMEOUT (use a 1 s timeout in the test config), keyset paging over 1 000 rows returns disjoint, ordered pages including equal sort keys; jqwik round-trip for `CursorCodec`.

## Step 7 — Migrations baseline and DB roles · `feat(db): flyway baseline and database roles`

- `db/MigrationRunner` — Flyway as `ums_owner`, location `classpath:db/migration`, schema `ums`, `validateOnMigrate=true`, `cleanDisabled=true` unless `Environment.TEST`.
- `scripts/db/create_roles.sql` — idempotent (`DO $$ … IF NOT EXISTS … $$`): roles `ums_owner`, `ums_app`, `ums_dev_console`, `ums_listener` (no default privileges; grants in `V2__p0_listener_grants.sql`), `ums_backup`; database grants; `ALTER DEFAULT PRIVILEGES FOR ROLE ums_owner IN SCHEMA ums` granting DML on tables + USAGE on sequences to `ums_app`, SELECT to `ums_dev_console` and `ums_backup`; `ALTER ROLE ums_dev_console SET statement_timeout = '15s'`. Passwords read from psql variables, never hard-coded.
- `ums-core/src/main/resources/db/migration/V1__p0_baseline.sql`:
  - `CREATE SCHEMA IF NOT EXISTS ums`; `CREATE EXTENSION IF NOT EXISTS btree_gist`.
  - `app_error_log` (design columns + index on `occurred_at DESC`).
  - `feature_flag (key text PK, enabled boolean not null, updated_by bigint, updated_at timestamptz)` seeded with the five flags, all enabled.
  - `backup_log (id, started_at, finished_at, file_path, size_bytes, verified boolean, outcome, message)`.
  - `activity_log` + three indexes + `notify_activity()` trigger, exactly as below (constraints named per the naming rule; matches V1) (`user_id` FK to `app_user` added in P1):
    ```sql
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
    ```
  - `change_history (id, occurred_at, actor_id, entity_type, entity_id, before jsonb, after jsonb, reason)` + index `(entity_type, entity_id, occurred_at DESC)`.
- Tests: migrate blank DB twice (idempotent), `ums_app` cannot `CREATE TABLE`, `ums_dev_console` cannot `INSERT`, NOTIFY fires on insert into `activity_log`.

### STOP B — "Database foundation"
Include the migration file, the roles script, and IT results. Await review.

---

## Step 8 — Interceptor framework · `feat(platform): facade proxy and interceptor chain`

- `invoke/Interceptor { Object intercept(Invocation inv) throws Throwable; InterceptorSlot slot(); }`
- `invoke/InterceptorSlot` enum in fixed order: `ERROR_MAPPING, SESSION, AUTHORISATION, FEATURE_FLAG, RATE_LIMIT, TRACING, AUDIT, TRANSACTION`.
- `invoke/Invocation` — target, `Method`, args (defensive copy), attribute map, `proceed()` advancing an index over an immutable interceptor list.
- `invoke/FacadeProxyFactory.create(Class<T> api, T impl, List<Interceptor>)` — sorts by slot once, rejects duplicate slots, returns a JDK proxy; `equals/hashCode/toString` bypass the chain; unwraps `InvocationTargetException`.
- Implement now: `ErrorMappingInterceptor` (uses `ApiErrorTranslator`), `TracingInterceptor` (minimal `Tracer`, `TraceContext` in ThreadLocal, MDC `traceId`, per-method `LongAdder` count/total/errors in `MetricsRegistry`), `TransactionInterceptor` (`@Writes` → read-write else read-only; delegates to `TransactionRunner`, which already owns the outermost retry — no second retry layer).
- SESSION, AUTHORISATION, AUDIT, RATE_LIMIT arrive in P1/P3; FEATURE_FLAG in P6. The factory works with any subset.
- Complexity: O(k) per call, k ≤ 8; O(1) extra space.
- Tests: slot order regardless of list order; duplicate slot rejected; exception from target mapped to `UmsApiException`; tx commits on success and rolls back on exception; retry stops after 3 attempts in total (2 retries); Object methods bypass; jqwik over random interceptor subsets.

## Step 9 — Generic state machine · `feat(platform): generic state machine with guards and actions`

- `state/StateMachine<S extends Enum<S>, C>` built via `StateMachine.builder(Class<S>)…transition(from, to, Guard<C>, Action<C>)…build()`; immutable; backed by `EnumMap<S, EnumMap<S, Transition<S,C>>>`.
- `fire(S current, S target, C ctx)` → runs guard; fail → `GuardFailedException(code, details)`; missing transition → `InvalidTransitionException`; then action; returns target.
- `allowedFrom(S)` returns an unmodifiable `EnumSet`.
- `Guard<C>` returns `GuardResult.pass()` or `GuardResult.fail(ErrorCode, Map)`; `Action<C>` is `void apply(C)`.
- Complexity header: O(1) lookup, O(|S|²) space worst case.
- Tests: demo `TrafficLight` enum machine — every (from, to) pair; jqwik random event sequences never yield a state outside `allowedFrom`; guard failure leaves state unchanged; action exceptions propagate.

## Step 10 — Executors and bounded structures · `feat(platform): managed executors, scheduler and bounded ring`

- `concurrent/NamedThreadFactory` (name prefix, daemon flag, uncaught handler → log + `app_error_log` hook interface).
- `concurrent/ManagedExecutors` — creates fixed pools with **bounded** `ArrayBlockingQueue` and `CallerRunsPolicy` or reject-with-`BusyException` (configurable), and scheduled pools; keeps registration order; `shutdownAll(Duration perStage)` stops in the order: accept-stop → registered drainers → scheduler → listeners → pools.
- `concurrent/Scheduler` — wraps `ScheduledExecutorService`; each job wrapped so an exception is logged and the schedule **continues**.
- `concurrent/BoundedRing<T>` — fixed `Object[]` + `AtomicLong` write index; `add` O(1); `snapshot()` O(capacity) newest-first.
- Tests: ring never exceeds capacity under 32 concurrent writers (`ConcurrencyHarness` — build a local version here, move to testkit in Step 12); failing scheduled job still runs next time; shutdown order recorded.

## Step 11 — Runtime, health and readiness · `feat(app): runtime lifecycle, health and readiness`

- `ums-app`: `UmsRuntime` with `start()` (load config → pools → migrate → start executors → mark ready) and `stop()` (shutdown sequence); idempotent; `AppContext` holds every object, wired by constructors.
- `platform/observe/HealthService` with `HealthProbe` strategy interface; P0 probes: `DatabaseReachableProbe` (`SELECT 1`, 2 s timeout), `MigrationsCurrentProbe`. Status `UP | DEGRADED | DOWN` with per-probe detail.
- `Readiness` (`AtomicBoolean`); until ready, any facade call fails with `DB_UNAVAILABLE` (wire this check as the first action of the SESSION slot later; for now a test-only facade proves it).
- `Main` — starts the runtime headless, logs health, installs a shutdown hook; run with `mvn -pl ums-app exec:java`.
- Tests (`ums-app` IT): start on blank DB → READY + UP; stop releases all connections (`pg_stat_activity` count back to baseline); DB down at start → `ConfigException`/`DataUnavailableException` with a clear message, no hang.

### STOP C — "Platform core"
Include complexity table for every class from Steps 8–11, error mapping table, state machine table for the demo machine, concurrency notes (threads, shutdown order). Await review.

---

## Step 12 — Test kit · `test: shared testkit with db extension, harnesses and architecture rules`

- `UmsDbExtension` (JUnit 5): reads `UMS_TEST_DB_*`; once per JVM (via the root `ExtensionContext.Store`, not a static field) runs Flyway clean + migrate; before each test truncates all `ums` tables except `flyway_schema_history` and seed tables (`TRUNCATE … RESTART IDENTITY CASCADE`, statement generated from the catalog once).
- `ConcurrencyHarness.run(int threads, Callable<T> task)` → `ConcurrencyResult<T>` (outcomes, exceptions, duration); all threads released by one `CountDownLatch`; honours `ums.concurrency.repeats`.
- `FailureInjector` — `terminateAppConnections()`, `exhaustPool(HikariDataSource)`, `blockTable(String)` (lock in a side connection).
- `ArchitectureRules` — shared ArchUnit rules: layer direction; `java.sql` only in `..repository..` and `edu.ums.platform.db..`; no non-final static fields (except `static final` constants and loggers); no cycles between `edu.ums.core.*` features; services never depend on facades; interceptors only in `edu.ums.platform.invoke..`; `*Repository` interfaces in `repository` packages. Each module has an `ArchitectureTest` applying them.
- `TestData` (root builder entry) and `SeedGenerator(long seed)` skeleton (deterministic; features add builders later).

## Step 13 — CI · `ci: github actions for push, pull request and nightly`

`.github/workflows/ci.yml`:
- `push` (all branches): checkout, `actions/setup-java` (Temurin 21, Maven cache), `mvn -B -Pfast verify`, Gitleaks action.
- `pull_request`: service `postgres:18` with health check; run `scripts/db/create_roles.sql` via `psql` with passwords from repository secrets; create `ums_test`; `mvn -B verify`; upload Surefire/Failsafe/JaCoCo/SpotBugs/licence reports as artifacts; run Dependency-Check if `NVD_API_KEY` secret is set.
- `schedule` nightly at 21:30 UTC (03:00 IST): `mvn -B verify -Pnightly` + Dependency-Check + PIT report (non-blocking until P7).
- Never print secrets; use `::add-mask::` for any generated value.

## Step 14 — Documentation · `docs: readme, adrs and runbook skeleton`

- `README.md`: prerequisites (JDK 21, Maven 3.9+, PostgreSQL 18, gitleaks), database setup (create `ums`, `ums_test`, run roles script), env vars, install hooks, build, run, test commands, module map.
- `docs/adr/001…009` from DESIGN.md §Decision records — each with Status (Accepted), Context, Decision, Consequences, Alternatives.
- `docs/runbook.md` headings: start/stop, health, backup, restore, incidents, rollback (filled in P6).
- `CHANGELOG.md` `[Unreleased]` lists P0 additions.

### STOP D — "P0 complete" (final)
Full STOP report covering all steps plus:
- Exit-gate evidence: CI run links (push + PR) green; local `mvn verify` output; `exec:java` start → READY/UP → clean stop log.
- Coverage numbers per module; licence report; Gitleaks result.
- Any deviation from DESIGN.md and the ADR it needs.
- Retrospective notes: what to change in the P1 prompt or the /hero templates.

Do **not** start P1. Await review.

---

## P0 must not

- Add any business table other than those listed in Step 7.
- Implement login, sessions, roles, audit writer, feature flag interceptor, or any facade method (they belong to later phases).
- Use static mutable state, `Thread.sleep` for synchronisation in production code, or unbounded queues.
- Weaken any check to get green.
