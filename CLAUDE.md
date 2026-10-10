# CLAUDE.md — Standing rules for the UMS backend

You are the coding agent for **UMS** (University Management System) backend: Java 21, Maven, PostgreSQL 18, five modules. These rules apply in **every** session. Read this file fully before any action.

## 0. Sources of truth (read before coding)

1. `docs/design/SRS.md` — requirements (FR-*, AC-*, MU-*, data rules). Approved.
2. `docs/design/SDLC.md` — phases P0–P7, Definition of Ready/Done, branching, testing gates. Approved.
3. `docs/design/DESIGN.md` — architecture, modules, API, schema, state machines, flows, concurrency, errors, security, ops. Approved.
4. `docs/prompts/00_MASTER_PROMPT.md` — the full build spec.
5. `docs/prompts/Pn_*.md` — the prompt for the current phase. **Work only on the current phase.**

If anything you are about to do conflicts with these, stop and ask. Never "improve" the design silently.

## 1. Working method

- One phase per session. Start a fresh session when context gets long; re-read this file first.
- Before writing code, inspect what already exists in the repo. Preserve working code and structure.
- Small steps. Each step = one focused change + its tests + one commit.
- **Failing test first** for every bug fix and for every acceptance criterion.
- Validate, don't assume: run the build and tests; read actual output; never claim a check passed without running it.
- Explain key decisions in the STOP report, not in code comments.
- You never declare your own work done. You stop at STOP points and wait for review.

## 2. The five engineering rules (mandatory in all code)

### 2.1 Object-oriented design
- Classes with one responsibility; small methods (≈ ≤ 40 lines).
- Interfaces for every service and repository; depend on interfaces; constructor injection only; all wiring in `AppContext`.
- Encapsulation: `private final` fields; no public mutable state; return unmodifiable collections.
- Composition over inheritance. Inheritance only for `BaseRepository` and the exception hierarchy.
- Domain data as immutable `record`s; closed sets as `enum`s; result variants as `sealed` interfaces.
- Named patterns where the design says so: Proxy + Chain of Responsibility (interceptors), Template Method (`BaseRepository`), Strategy (probes, guards), Observer (listeners), State machine (lifecycles).
- No logic in `static` methods except pure helpers. **No non-final `static` fields** (ArchUnit enforces).

### 2.2 Time and space complexity
- Every class that owns a non-trivial operation starts with a header comment:
  `// Complexity: time O(...) per <operation>; space O(...) — <what bounds it>`
- Choose data structures for both time and memory (`HashMap`/`HashSet` lookups, `TreeMap.floorEntry`, `EnumMap`, `ArrayBlockingQueue`, `LongAdder`, fixed rings).
- **Forbidden:** `OFFSET` paging, N+1 queries, unbounded collections/queues/caches/builders, loading whole tables or result sets into memory, regex over unbounded input.
- Lists use keyset paging on an indexed `(sort_key, id)`; page size ≤ 200. Heavy aggregation runs set-based in SQL.

### 2.3 State machines
- Every lifecycle in DESIGN.md (term, mark sheet, enrolment, account, session, attendance session, privacy request) uses the generic `StateMachine<S, C>` with explicit transitions, guards and actions.
- State changes persist with compare-and-set: `UPDATE … SET state=?, version=version+1 WHERE id=? AND state=? AND version=?`; 0 rows → `StaleDataException`.
- Each machine has a unit test of every (from, to) pair and a jqwik property test that random sequences never reach an illegal state.

### 2.4 Exception handling
- Only the `UmsException` hierarchy inside the backend; only `UmsApiException` crosses the facade boundary.
- Every error has an `ErrorCode` with a message key in `messages_en.properties`.
- Catch only at: the facade boundary, transaction retry, and documented best-effort paths (audit queue, listener dispatch). Never `catch (Exception e) {}`; never swallow without logging; always keep the cause.
- `try-with-resources` for every JDBC object, stream, and lock helper.
- Messages never contain SQL, stack traces, secrets, or other users' data.

### 2.5 Threads and concurrency
- Facades are stateless and thread-safe. Correctness under concurrency comes from the database: short transactions, conditional updates, row locks in the **global lock order term → student → section → mark sheet → result**, optimistic `version` checks.
- Never read-then-write a counter in Java. Never hold a transaction across user think-time.
- Shared in-memory state only via concurrent structures (`ConcurrentHashMap`, `AtomicLong`, `LongAdder`, `CopyOnWriteArrayList`, volatile immutable snapshots).
- Every executor: named threads, bounded queue, uncaught-exception handler, registered in the shutdown sequence.
- Every concurrency-sensitive feature has a `ConcurrencyHarness` test.

## 3. Layer and module rules (enforced by ArchUnit and Maven)

- `ums-api` depends on nothing. The frontend depends only on `ums-api`.
- Call direction: facade → interceptor chain → service → repository → DB. No skipping, no reverse calls.
- `java.sql` only in `repository` packages and `platform.db`.
- No cycles between feature packages.
- Repositories use `PreparedStatement` only. The Dev console is the single place that executes user SQL, under its own DB role.

## 4. Dependencies

- Only artifacts on the approved list in `00_MASTER_PROMPT.md` §3. To add anything else: stop and ask.
- Exact pinned versions in the parent POM; no ranges; no `SNAPSHOT`.
- Before adding any artifact, confirm it exists on Maven Central and note its licence in the STOP report.

## 5. Git rules

- Branch per phase topic: `phase-N/<topic>`. Never commit to `main` directly.
- One commit per step, Conventional Commits: `feat(reg): …`, `fix(marks): …`, `test: …`, `refactor: …`, `docs: …`, `chore: …`, `ci: …`.
- Never force-push, never rebase or amend pushed commits. Fix forward with new commits or `git revert`.
- Never commit secrets, `.env`, real personal data, or generated artifacts.

## 6. Checks to run before every commit

```
mvn -q -Pfast verify          # format check, compile, unit + property + ArchUnit tests
```
Before a STOP report, also run:
```
mvn -q verify                 # + integration, contract, concurrency, acceptance, JaCoCo, SpotBugs
```
Integration tests need `UMS_TEST_DB_URL`, `UMS_TEST_DB_USER`, `UMS_TEST_DB_PASSWORD`.

## 7. Definition of Done (per task)

- Code + tests committed; all checks above green.
- Each AC covered has a test named after its ID (`AC_REG_02_lastSeatContention`) and it passes.
- Service/domain line coverage ≥ 80 %.
- Complexity headers present and true; state machines match DESIGN.md; every new error has code + message key.
- Concurrency-sensitive code has a concurrency test.
- CHANGELOG entry; ADR for any design-level decision.
- STOP report written. **Review decides Done, not you.**

## 8. STOP report format (use exactly this)

```
## STOP REPORT — <phase> / <stop point name>

1. Summary — what was built; list of commits (hash + message)
2. Checks run — | command | result | notes |
3. Acceptance coverage — | AC ID | test | result |
4. Decisions made — anything not fully specified, with the reason
5. Assumptions and uncertainties — what you are unsure about
6. Complexity table — | class.method | time | space | bounded by |
7. Error mapping table — | ErrorCode | thrown where | from (exception / SQLState / constraint) |
8. State machine table (if touched) — | machine | from | to | guard | action | test |
9. Concurrency notes — new threads, shared state, locks taken and their order
10. Deviations from design — and the ADR needed, if any
11. Proposed next step — do NOT start it

Awaiting review.
```

## 9. Never do

- Never start the next step or phase before review approves the STOP report.
- Never change approved requirements, schema rules, error codes or state machines without asking.
- Never add Spring, Hibernate/JPA, Lombok, Guava, or any unapproved dependency.
- Never disable, skip, or weaken a test or check to make the build green. Flaky test → fix it or quarantine with `@Tag("quarantine")` and report it.
- Never use real personal data; test data comes from `ums-testkit` factories only.
- Never log passwords, hashes, tokens, or personal fields.
- Never generate a large amount of code in one step without tests.
