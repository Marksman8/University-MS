# UMS Backend — Master Coding Prompt

> Place this file at `docs/prompts/00_MASTER_PROMPT.md`. Export the three approved docs (SRS, SDLC, System Design) as Markdown into `docs/design/SRS.md`, `docs/design/SDLC.md`, `docs/design/DESIGN.md`. `CLAUDE.md` at the repo root holds the standing rules. This file is the build spec for all phases; each phase also gets its own prompt (`Pn_*.md`) with exact steps.

---

## 1. Context

Build the backend of a University Management System for one institution, from scratch (no existing code is reused). Four roles — Student, Faculty, Admin, Dev — run a full academic term: setup, registration, attendance, marks, results, notices, reports, plus developer diagnostics. A JavaFX frontend (designed separately by the owner) runs in the **same JVM** and calls the backend **only** through role facades in `ums-api`.

- Tier: **full production** (feature flags, health checks, alerts, backups, mutation/load/soak testing, privacy requests).
- Fees are **out of scope** for this release.
- Java 21, Maven 3.9+, PostgreSQL 18, Windows + Fedora Linux.
- Defaults (configurable): credit limit 26/term; attendance threshold 75 %; attendance edit window 48 h; lockout after 5 failures for 15 min; session idle 30 min; 10-point grade scale O≥90→10, A+≥80→9, A≥70→8, B+≥60→7, B≥50→6, C≥45→5, P≥40→4, F<40→0.
- Decided rules: **repeat attempts — all attempts count in CGPA** (each attempt is its own enrolment and result; credits earned count once, from the passing attempt; both attempts on the transcript). **Early grading** — Admin may move a term to GRADING before its end date only with explicit confirmation (`confirmEarly = true`), else `CONFIRMATION_REQUIRED`.

## 2. Requirements summary (full text in SRS.md)

| Group | Requirements |
| --- | --- |
| AUTH | Login (BCrypt cost 12), lockout 5/15 min, idle timeout 30 min, change/reset password (min 10 chars, reset forces change), admin user lifecycle, never hard-delete users, last-admin rule |
| ORG | Departments, programmes, courses (+ prerequisites, active flag), grade scale, student→programme+batch, faculty→department |
| TERM | Term with dates + registration window; lifecycle PLANNED→REGISTRATION_OPEN→IN_PROGRESS→GRADING→RESULTS_PUBLISHED→CLOSED; guards; one active term |
| OFFER | Sections (capacity, faculty, weekly slots, room), room/faculty clash rejection, assessment scheme weights = 100 % |
| REG | Register with five checks (prereq, same course, credits ≤ 26, seat, timetable clash), atomic seat, drop in window, admin override with reason, specific reason codes, rate limit 10/min |
| ATT | Class sessions, PRESENT/ABSENT/EXCUSED, 48 h edit window then admin correction with reason, attendance % (excused excluded), eligibility flag < 75 % with admin override |
| MARKS | Marks 0..max or AB, mark sheet DRAFT→SUBMITTED→APPROVED→PUBLISHED (return, reopen), grades on approval, SGPA/CGPA on publish, revisions as new versions, transcript |
| TT | Timetables derived from enrolments and assignments |
| NOTICE | Audience ALL / ROLE / PROGRAMME / SECTION, publish + expiry dates |
| REPORT | Admin and faculty reports, streamed CSV with formula-injection escaping |
| AUDIT | Activity log for every write/login/logout/failure/denial; change history (before/after) for marks, attendance, results, eligibility, roles |
| DEV | Query console (read-only default, gated writes, no DDL), pipeline view, diagnostics, live activity feed |
| API | Four facades, immutable DTOs, typed errors, keyset paging, thread-safe blocking calls, listeners |
| PRIV | Data export (JSON) and contact-data deletion requests with admin approval |
| OPS | Health/readiness, metrics + alerts, feature flags, daily backups + restore, semver releases with rollback |

Acceptance criteria in scope (each becomes a test named after its ID): AC-AUTH-01..04, AC-SEC-01..02, AC-TERM-01..02, AC-OFFER-01..02, AC-REG-01..04, AC-ATT-01..03, AC-MARKS-01..05, AC-NOTICE-01, AC-AUDIT-01, AC-API-01, AC-E2E-01, AC-PRIV-01..02, AC-OPS-01..03. Exact Given/When/Then in SRS.md.

Misuse cases MU-01..MU-12 in SRS.md each need at least one test.

## 3. Stack and approved dependencies

Pin exact latest stable versions in the parent POM (verify each on Maven Central; no ranges, no SNAPSHOT).

**Runtime:** `org.postgresql:postgresql` · `com.zaxxer:HikariCP` · `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` · `org.mindrot:jbcrypt` · `com.fasterxml.jackson.core:jackson-databind` + `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` · `org.slf4j:slf4j-api` · `ch.qos.logback:logback-classic`

**Test:** `org.junit.jupiter:junit-jupiter` · `org.assertj:assertj-core` · `org.mockito:mockito-core` + `mockito-junit-jupiter` · `net.jqwik:jqwik` · `com.tngtech.archunit:archunit-junit5`

**Build plugins:** compiler, surefire, failsafe, `maven-enforcer-plugin`, `spotless-maven-plugin` (google-java-format), `maven-checkstyle-plugin`, `spotbugs-maven-plugin` + FindSecBugs, `jacoco-maven-plugin`, `pitest-maven`, `dependency-check-maven`, `license-maven-plugin` (org.codehaus.mojo), `exec-maven-plugin` (org.codehaus.mojo, to run `Main`), `maven-dependency-plugin` (only the `properties` goal, to pass the Mockito agent jar path to Surefire/Failsafe as `-javaagent:${org.mockito:mockito-core:jar}`). Maven's default lifecycle plugins (clean, resources, jar, install, deploy, site) are pinned too.

Nothing else without asking. Banned: Spring, Hibernate/JPA, Lombok, Guava, Apache Commons (unless approved).

## 4. Architecture

Modular monolith, one JVM. Request path:

```
JavaFX (owner's) → role facade (ums-api interface)
  → JDK proxy interceptor chain (ums-platform):
      1 ErrorMapping (outermost) → 2 Session → 3 Authorisation → 4 FeatureFlag
      → 5 RateLimit → 6 Tracing → 7 Audit → 8 Transaction (innermost, with retry)
  → feature service (ums-core) → repository (JDBC) → HikariCP → PostgreSQL
Side channels: AuditWriter thread (activity_log, async) · ChangeHistoryWriter (same tx, sync)
               · ActivityListener (LISTEN/NOTIFY) · Scheduler (health, alerts, flags, purge, backup)
```

### Modules

| Module | Contents | Depends on |
| --- | --- | --- |
| `ums-api` | `UmsClient`, `Session`, `StudentApi`, `FacultyApi`, `AdminApi`, `DevApi`, DTO records, typed id records, `Page`/`Cursor`, `ErrorCode`, `UmsApiException`, listener interfaces, annotations `@Writes @Audited @Feature @RateLimited` | — |
| `ums-platform` | config, db (pools, Tx, BaseRepository, KeysetPager), invoke (proxy + interceptors), security primitives, error hierarchy + mapping, audit infra, observe (tracer, metrics, health, alerts), flags, schedule, `StateMachine` | api |
| `ums-core` | feature packages `auth org term offer reg att marks timetable notice report priv dev`, each with `domain/ service/ repository/`; `facade/` implementations; Flyway migrations; i18n bundle | api, platform |
| `ums-app` | `AppContext` (manual wiring), `Main`, startup/shutdown | all |
| `ums-testkit` | builders, `SeedGenerator`, `UmsDbExtension`, `ConcurrencyHarness`, `FailureInjector`, `ArchitectureRules`, `LoadRunner` | api, platform (used as a test-scope dependency by core and app) |

Acceptance and end-to-end tests that need the fully wired application live in `ums-app/src/test`. (This avoids a Maven reactor cycle: testkit must not depend on core, because core's tests depend on testkit.)

Base package `edu.ums`. Folder layout as in DESIGN.md §Modules.

## 5. Application API contract

```java
public interface UmsClient { Session login(String username, char[] password); }

public interface Session extends AutoCloseable {
    UserSummary user(); Role role(); boolean mustChangePassword();
    void changePassword(char[] current, char[] next);
    StudentApi student(); FacultyApi faculty(); AdminApi admin(); DevApi dev(); // ACCESS_DENIED on wrong role
    Subscription onNotice(NoticeListener listener);
    @Override void close();                                                     // logout
}
```

Facade operations are listed in DESIGN.md §Application API; each phase prompt gives exact signatures for its methods. Rules:

- DTOs are `record`s; ids are typed records (`StudentId(long value)`, `SectionId`, `TermId`, …).
- Mutable DTOs carry `long version`; writes pass it back.
- Lists: `Page<T>(List<T> items, Optional<Cursor> next)`; `Cursor` is an opaque Base64 keyset token; page size 1..200.
- `OffsetDateTime` (UTC) for times; `BigDecimal` for percentages and grade points.
- All failures: `UmsApiException(ErrorCode code, String messageKey, Map<String,Object> args)` (unchecked), documented with `@throws` per method.
- Calls block, are thread-safe, must not be called on the JavaFX thread; listener callbacks arrive on backend threads.
- Reports stream to a caller-supplied `OutputStream`.

## 6. Data model

Schema `ums`, 31 tables (full list in DESIGN.md §Data model). Every table: `id bigint generated always as identity` (except UUID session ids and composite-key link tables), `created_at timestamptz default now()`; mutable aggregates have `version bigint not null default 0`. All timestamps `timestamptz`; money/percent `numeric`.

Hard rules as constraints (must exist exactly):

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE section ADD CONSTRAINT ck_section_capacity CHECK (enrolled_count BETWEEN 0 AND capacity);
CREATE UNIQUE INDEX uq_enrolment_live ON enrolment (student_id, term_id, course_id) WHERE status = 'ENROLLED';
ALTER TABLE section_slot ADD CONSTRAINT ex_room_slot
  EXCLUDE USING gist (term_id WITH =, room WITH =, day WITH =, minutes WITH &&);
ALTER TABLE faculty_slot ADD CONSTRAINT ex_faculty_slot
  EXCLUDE USING gist (term_id WITH =, faculty_id WITH =, day WITH =, minutes WITH &&);
CREATE UNIQUE INDEX uq_one_active_term ON term ((true)) WHERE state IN ('REGISTRATION_OPEN','IN_PROGRESS');
CREATE UNIQUE INDEX uq_result_current ON result (enrolment_id) WHERE is_current;
ALTER TABLE mark ADD CONSTRAINT ck_mark_nonneg CHECK (value IS NULL OR value >= 0);
```

Migrations: Flyway, versioned `V<n>__<phase>_<topic>.sql`, expand-then-contract (a release only adds; removals ship one release later). Flyway runs as `ums_owner`; the app runs as `ums_app`. DB roles: `ums_owner`, `ums_app`, `ums_dev_console` (read-only default, no `password_hash`, `statement_timeout 15s`), `ums_backup`.

## 7. State machines

Generic `StateMachine<S extends Enum<S>, C>` in `ums-platform` (`EnumMap<S, EnumMap<S, Transition<S,C>>>`, O(1) lookup); `Transition` = `Guard<C>` + optional `Action<C>`; persisted by compare-and-set on `(state, version)`.

| Machine | Transitions (guard) |
| --- | --- |
| Term (Admin) | PLANNED→REGISTRATION_OPEN (≥1 section; each has faculty, slots, scheme = 100 %; no other active term) · →IN_PROGRESS (creates DRAFT mark sheets) · →GRADING (before end date needs `confirmEarly`) · →RESULTS_PUBLISHED (all sheets APPROVED; computes SGPA/CGPA; sheets→PUBLISHED; enrolments→COMPLETED) · →CLOSED |
| Mark sheet | DRAFT→SUBMITTED (faculty of section; term IN_PROGRESS or GRADING; complete for eligible students) · SUBMITTED→DRAFT (admin return + comment) · SUBMITTED→APPROVED (admin; term GRADING; computes results) · APPROVED→DRAFT (admin reopen + reason; term GRADING; discards computed results) · APPROVED→PUBLISHED (system on term publish) |
| Enrolment | ENROLLED→DROPPED (student in window; admin until GRADING) · ENROLLED→COMPLETED (publish) |
| Account | ACTIVE→LOCKED (5th failure) · LOCKED→ACTIVE (15 min or admin) · ACTIVE⇄DEACTIVATED (admin; not last active admin) |
| Session | ACTIVE→EXPIRED (idle 30 min) · ACTIVE→CLOSED (logout) |
| Attendance session | EDITABLE→LOCKED derived from `recorded_at + 48h` (not stored); LOCKED: admin correction with reason only |
| Privacy request | REQUESTED→APPROVED→COMPLETED · REQUESTED→REJECTED |

## 8. Key flows (exact order; see DESIGN.md §Key flows)

- **Register:** term `FOR SHARE` (REGISTRATION_OPEN, in window) → student `FOR UPDATE` → already-enrolled → prerequisites (`HashSet` difference) → credits → timetable overlap → `UPDATE section SET enrolled_count=enrolled_count+1, version=version+1 WHERE id=? AND enrolled_count<capacity` (0 rows → `SECTION_FULL`) → insert enrolment + zero `attendance_summary` → commit → audit `REG_ENROL`.
- **Marks:** sheet `FOR UPDATE`; DRAFT + version match + section ownership; existing marks in `HashMap`; validate; one batch upsert; `change_history` for changes.
- **Approve:** term `FOR SHARE` (GRADING) → sheet `FOR UPDATE` → total = Σ(value ÷ max × weight), `BigDecimal`, scale 2, HALF_UP; AB = 0; grade via `TreeMap.floorEntry`; batch insert results.
- **Publish:** term `FOR UPDATE` → guard count → bulk updates → `INSERT INTO term_result … SELECT … GROUP BY` (SGPA = Σ credits×GP ÷ Σ credits, all attempts; CGPA over all current results in published terms) → CAS term state.
- **Revise result:** result row lock → new version current, old not current → `change_history` → recompute that student's term_result and later CGPAs.

## 9. Concurrency model

- READ COMMITTED; one transaction per facade call; statement timeout 10 s; lock wait 5 s.
- Global lock order: **term → student → section → mark sheet → result**.
- Retry 40001/40P01 up to 3× with 20–100 ms random backoff → `CONFLICT_TRY_AGAIN`.
- Threads: callers (any) · `audit-writer` 1 · `activity-listener` 1 · `scheduler` 2 · `listener-dispatch` 2 · `dev-console` 2. Pools: app 10 (5 s wait), console 3, listener 1 dedicated.
- Shutdown: stop accepting calls → drain audit (≤5 s) → scheduler → listener → pools.
- Shared state: sessions `ConcurrentHashMap` + `AtomicLong`; rate limits per-session `TokenBucket` (`synchronized` per bucket); flags volatile immutable snapshot (10 s refresh); metrics `LongAdder`; subscribers `CopyOnWriteArrayList`.

## 10. Errors

Hierarchy (unchecked): `UmsException` → `DomainException` (`ValidationException`, `RuleViolationException`, `InvalidTransitionException`, `GuardFailedException`, `NotFoundException`, `StaleDataException`) · `SecurityViolationException` (`AuthenticationException`, `AccessDeniedException`) · `PolicyException` · `InfrastructureException` (`DataUnavailableException`, `BusyException`, `ConflictException`). Unknown throwables → `INTERNAL_ERROR` + incident id.

`ErrorCode` enum (each with key `error.<area>.<lower_snake>`):
`BAD_CREDENTIALS ACCOUNT_LOCKED SESSION_EXPIRED ACCESS_DENIED PASSWORD_POLICY LAST_ADMIN USERNAME_TAKEN FEATURE_DISABLED RATE_LIMITED CONFIRMATION_REQUIRED VALIDATION_FAILED NOT_FOUND STALE_DATA INVALID_TRANSITION GUARD_FAILED SLOT_CLASH INVALID_SCHEME WINDOW_CLOSED ALREADY_ENROLLED PREREQ_NOT_MET CREDIT_LIMIT TIMETABLE_CLASH SECTION_FULL EDIT_WINDOW_CLOSED INVALID_SESSION_DATE MARK_OUT_OF_RANGE NOT_EDITABLE MARKS_INCOMPLETE DB_UNAVAILABLE BUSY_TRY_AGAIN CONFLICT_TRY_AGAIN TIMEOUT CANCELLED INTERNAL_ERROR`

SQL mapping (`SqlErrorMapper`; constraint name → code via `HashMap`): 08xxx→DB_UNAVAILABLE · Hikari timeout→BUSY_TRY_AGAIN · 40001/40P01→retry→CONFLICT_TRY_AGAIN · 23505 `uq_enrolment_live`→ALREADY_ENROLLED · 23505 username→USERNAME_TAKEN · 23P01 `ex_room_slot`/`ex_faculty_slot`→SLOT_CLASH · 23514 `ck_section_capacity`→SECTION_FULL · 23503→NOT_FOUND · 55P03→BUSY_TRY_AGAIN · 57014→TIMEOUT/CANCELLED · else INTERNAL_ERROR.

## 11. Security

BCrypt 12, `char[]` zeroed; uniform BAD_CREDENTIALS; lockout via one conditional update; 128-bit random session ids; role check in interceptor + ownership checks in services (`OwnershipGuard`); last-admin rule under lock; `PreparedStatement` only, sort/filter columns from enum allow-lists; secrets from env vars only (`UMS_DB_URL UMS_DB_USER UMS_DB_PASSWORD UMS_CONSOLE_DB_PASSWORD UMS_LISTENER_DB_PASSWORD UMS_OWNER_DB_PASSWORD UMS_BACKUP_DB_PASSWORD UMS_ENV`); CSV cells starting `= + - @` prefixed with `'`; audit of every write, login, failure, denial, override.

## 12. Operations

Logback with MDC (trace id, user id, role), UTC, daily rolling 14 days; no PII. Activity log async (queue 10,000 → batch ≤100 / 500 ms → NOTIFY). Change history sync in the same transaction. Health probes: DB reachable, migrations current, pools not saturated, audit queue < 80 %, last backup < 26 h, disk > 10 % free. Readiness gate before facades serve. Alerts every 30 s (error rate > 5 %/5 min, probe DOWN, backup missed, audit drops > 0) → Dev banner + ERROR log. Feature flags `REGISTRATION MARKS_ENTRY RESULT_PUBLISH NOTICES PRIVACY_EXPORT` (DB table, 10 s cache). Daily `pg_dump -Fc` as `ums_backup`, verify with `pg_restore --list`, keep 30 days, weekly copy to second path. Nightly purge in batches of 5,000 per SQL data-retention table.

## 13. Complexity targets

As in DESIGN.md §Time and space complexity — e.g. registration checks O(log n + q + c·s) time, O(c·s + q) space; any list O(log n + p) / O(p); publish set-based in SQL, O(1) Java memory; audit enqueue O(1) with fixed O(10,000) space; reports streamed O(1) per row. Every owning class carries its `// Complexity:` header.

## 14. Testing (gates from SDLC.md)

Unit, jqwik property (classifier, grade/GPA, state machines, slot clash, validators), ArchUnit, integration on PostgreSQL 18, contract (facade DTO shapes + error codes), concurrency (`ConcurrencyHarness`; 50 repeats nightly), failure injection, acceptance (`AC_*`), JaCoCo ≥ 80 % service/domain, PIT ≥ 70 % before release, SpotBugs+FindSecBugs 0 high, OWASP Dependency-Check 0 high CVEs, Gitleaks 0, load p95 < 200 ms at 10,000 students, 1 h soak flat heap, localisation keys complete. Test data only from `ums-testkit`.

## 15. Phases

| Phase | Prompt | Scope |
| --- | --- | --- |
| P0 | `P0_FOUNDATION.md` | Build, quality gates, CI, platform core (config, errors, db, tx, migrations baseline, interceptor framework, state machine, executors), app start/stop, testkit |
| P1 | `P1_IDENTITY_AUDIT.md` | AUTH, sessions, authorisation, audit + change history, activity listener, AC-AUTH, AC-SEC, AC-API |
| P2 | `P2_STRUCTURE_TERMS.md` | ORG, TERM, OFFER |
| P3 | `P3_REGISTRATION.md` | REG |
| P4 | `P4_TEACHING.md` | ATT, TT, NOTICE |
| P5 | `P5_RESULTS.md` | MARKS |
| P6 | `P6_OPS_DEV_PRIV.md` | DEV, OPS, PRIV, REPORT |
| P7 | `P7_RELEASE.md` | Load, soak, mutation, security, UAT, docs, installers, v1.0.0 |

Each phase prompt is written after the previous phase's final STOP report is approved, so it reflects what was actually built.

## 16. Must not

Everything in `CLAUDE.md` §9, plus: never reuse or copy code from the old Student module; never implement fees; never expose internal classes to `ums-api`; never call facades from inside services (services call services through interfaces); never put SQL in services; never let a facade method open more than one transaction.
