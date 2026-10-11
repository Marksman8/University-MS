# PROTO — Full-slice working prototype in 60 minutes (backend + JavaFX frontend)

> Place at `docs/prompts/PROTO_FULL_SLICE.md`. Read `CLAUDE.md` first; this prompt **overrides** it where they conflict (listed in §9).
> **Goal:** in about 60 minutes, a runnable desktop app where all four roles log in and one academic term runs end to end:
> setup → registration → attendance → marks → approval → published results → transcript, with every action in the activity log and visible to Dev.
> **This is a prototype.** It lives on its own branch and its own database. It is never merged into `main`. Production phases P1–P7 continue separately and may reuse ideas from it, not its code by default.

---

## 0. Ground rules for speed (read first)

- **Pause P1.** If you were working on P1, commit or stash cleanly first and report where you stopped (one line).
- Branch: `prototype/full-slice` from `main` (`8e54e71` or later). Never push to `main`, never open a PR into `main`.
- Database: a separate database **`ums_proto`** on the same PostgreSQL 18 test cluster (same roles; create it the way README "Database setup" creates `ums`, owner `ums_owner`, schema `ums`). Set `UMS_DB_URL` to it for the prototype run.
- Migrations: numbered **V900+** (`V900__proto_schema.sql`, `V901__proto_grade_scale.sql`) so they can never collide with P1's V3+. Do not edit V1/V2.
- Work in **time boxes**. Run `date` at the start and at every checkpoint. At each checkpoint print a 3-line status (done / doing / at risk). If you are behind, **cut from the bottom of the priority list in §8**, never from Tier 1.
- Commit at every checkpoint (`feat(proto): …`). `mvn -q -Pfast verify` must be green at every commit (it skips the JaCoCo gate; the full `verify` is **not** required on this branch).
- Do not ask questions mid-way. Make the most reasonable choice, note it in the final report, and keep going.

## 1. What must work at the end (the demo)

Seed data puts a term in `REGISTRATION_OPEN`. Then, in the UI:

1. **Student** logs in → sees open sections → registers for 3 → one registration fails with a clear reason (full section or timetable clash) → sees timetable.
2. **Admin** advances the term to `IN_PROGRESS` (mark sheets created as DRAFT).
3. **Faculty** logs in → sees own sections → records attendance for a date → enters marks for both components → submits the mark sheet.
4. **Admin** advances the term to `GRADING` → approves each submitted sheet → publishes results.
5. **Student** sees results per course (total %, grade, grade point), SGPA, CGPA, attendance %.
6. **Dev** logs in → sees every step above in the live activity feed with timestamps → runs `SELECT` queries in the SQL console and sees results in a table → sees health UP.

## 2. Stack additions (approved for this branch only)

- `org.openjfx:javafx-controls` 21.0.x (latest 21.0.x on Maven Central) and `org.openjfx:javafx-maven-plugin` 0.0.8. Licence GPL-2.0 with Classpath Exception (already on the allow-list).
- Nothing else. Reuse P0: `TransactionRunner`, `BaseRepository`, `KeysetQuery`, `SqlErrorMapper`, `ErrorCode`/`UmsApiException`, `StateMachine`, `FacadeProxyFactory` + `ErrorMappingInterceptor`, `TracingInterceptor`, `TransactionInterceptor`, `DataSources` (app + console pools), `UmsRuntime`, `HealthService`.

## 3. Modules and packages

- New module **`ums-ui`** (depends on `ums-api` + `ums-app`; `ums-app` provides the wired `UmsClient`). Main class `edu.ums.ui.UmsDesktopApp`. Run with `mvn -pl ums-ui javafx:run`.
- Backend code in `ums-core` under **`edu.ums.proto.<feature>`** (`auth`, `org`, `term`, `reg`, `att`, `marks`, `dev`, `audit`), each with `domain/ service/ repository/` as usual, so ArchUnit rules still pass. The `edu.ums.proto` prefix keeps it clearly separate from the production packages that P1+ will build.
- Facade methods are **added to the existing `ums-api` interfaces** (`StudentApi`, `FacultyApi`, `AdminApi`, `DevApi`) plus a `ProtoClient implements UmsClient` in `ums-app`.

## 4. Schema — `V900__proto_schema.sql` (all constraints/indexes named, as always)

```text
app_user(id, username UQ uq_app_user_username, password_hash, role CK, full_name, active, failed_attempts, locked_until, created_at)
department(id, code UQ, name)
programme(id, code UQ, name, department_id FK)
course(id, code UQ, title, credits CK 1..6, department_id FK)
course_prereq(course_id FK, prereq_id FK, PK both)
student(user_id PK FK, programme_id FK, roll_no UQ)
faculty(user_id PK FK, department_id FK)
term(id, name, start_date, end_date, reg_open, reg_close, state CK, version)
section(id, term_id FK, course_id FK, code, capacity, enrolled_count, faculty_id FK,
        day CK 1..6, start_min, end_min, room, version,
        CONSTRAINT ck_section_capacity CHECK (enrolled_count BETWEEN 0 AND capacity),
        CONSTRAINT ck_section_time CHECK (start_min < end_min))
assessment_component(id, section_id FK, name, max_marks, weight_pct)       -- weights per section sum to 100 (checked in service)
enrolment(id, student_id FK, section_id FK, term_id FK, course_id FK, status CK ENROLLED|DROPPED|COMPLETED, version)
   + CREATE UNIQUE INDEX uq_enrolment_live ON enrolment (student_id, term_id, course_id) WHERE status = 'ENROLLED'
class_session(id, section_id FK, session_date, recorded_at, UQ (section_id, session_date))
attendance(session_id FK, enrolment_id FK, status CK PRESENT|ABSENT|EXCUSED, PK both)
mark_sheet(id, section_id UQ FK, state CK DRAFT|SUBMITTED|APPROVED|PUBLISHED, version)
mark(enrolment_id FK, component_id FK, value numeric(6,2), absent bool, PK both,
     CONSTRAINT ck_mark_nonneg CHECK (value IS NULL OR value >= 0))
result(id, enrolment_id UQ FK, total_pct numeric(5,2), grade, grade_point numeric(4,2))
term_result(student_id FK, term_id FK, sgpa numeric(4,2), cgpa numeric(4,2), credits_earned, PK both)
grade_scale(grade PK, min_percent, grade_point)   -- V901 seeds O 90→10, A+ 80→9, A 70→8, B+ 60→7, B 50→6, C 45→5, P 40→4, F 0→0
```

Plus `ALTER TABLE activity_log ADD CONSTRAINT fk_activity_log_user FOREIGN KEY (user_id) REFERENCES app_user (id)`. Indexes for the hot reads: `enrolment (student_id, term_id)`, `enrolment (section_id, status)`, `section (term_id)`, `section (faculty_id, term_id)`.

## 5. Backend behaviour (keep these rules even in the prototype)

**Auth.** BCrypt cost 12; `char[]` zeroed; uniform `BAD_CREDENTIALS`; lockout after 5 failures for 15 min via one conditional `UPDATE … RETURNING` (Tier 3 — skip if behind). `ProtoClient.login` returns a `Session`; `session.student()/faculty()/admin()/dev()` returns that role's facade or throws `ACCESS_DENIED`. **Student and faculty methods take no user id** — they use the session's own id, so a student can never act for another student.

**Facades** are built with `FacadeProxyFactory` and the P0 interceptors (error mapping, tracing, transaction). Writes are `@Writes`.

**Term lifecycle** — `StateMachine` with guards:
- `REGISTRATION_OPEN → IN_PROGRESS`: creates a DRAFT `mark_sheet` per section.
- `IN_PROGRESS → GRADING`.
- `GRADING → RESULTS_PUBLISHED`: guard "every sheet APPROVED", else `GUARD_FAILED` listing section codes. Action: sheets → PUBLISHED, enrolments → COMPLETED, `term_result` computed **set-based in one `INSERT … SELECT … GROUP BY`**.
- Transitions persist with compare-and-set on `(state, version)`.

**Registration** (exact order, one transaction):
term `FOR SHARE` (must be REGISTRATION_OPEN) → student row `FOR UPDATE` → already enrolled in course (`ALREADY_ENROLLED`) → prerequisites passed (`PREREQ_NOT_MET`, Tier 3) → credits ≤ 26 (`CREDIT_LIMIT`) → timetable overlap with the student's other sections that term (`TIMETABLE_CLASH`, same day and `start < otherEnd && otherStart < end`) → **`UPDATE section SET enrolled_count = enrolled_count + 1, version = version + 1 WHERE id = ? AND enrolled_count < capacity`** (0 rows → `SECTION_FULL`) → insert enrolment. Drop reverses the seat with `enrolled_count - 1`.

**Attendance.** Faculty records a session for **own** section and a date (not in the future); one row per enrolled student; percent = present ÷ (present + absent) × 100, excused excluded, `BigDecimal` scale 2 HALF_UP.

**Marks.** Faculty enters marks for own section only while the sheet is DRAFT (`NOT_EDITABLE` otherwise); `0 ≤ value ≤ max` or absent (`MARK_OUT_OF_RANGE`); one batch upsert. Submit: DRAFT → SUBMITTED only if every enrolled student has a value or absent for every component (`MARKS_INCOMPLETE`). Admin approve: SUBMITTED → APPROVED, computes per student total = Σ(value ÷ max × weight), absent = 0, `BigDecimal` scale 2 HALF_UP; grade via `TreeMap.floorEntry` on the scale. Admin return: SUBMITTED → DRAFT (Tier 2).

**GPA.** SGPA = Σ(credits × grade point) ÷ Σ credits for the term. CGPA = same over all results in published terms, **all attempts count**. Credits earned exclude F. Round to 2 places.

**Activity log.** Every login (success and failure), logout, write and denied access inserts an `activity_log` row **synchronously in the same transaction** (prototype simplification; P1 makes it async). Failures after rollback are logged in a separate short transaction. Never log passwords or hashes.

**Dev.**
- SQL console: exactly one statement, must start with `SELECT` or `WITH` (skip leading whitespace and `--` comments with a char loop, no regex), runs on the **console pool** (`ums_dev_console`, read-only), `setMaxRows(201)`, `setQueryTimeout(5)`. Returns columns + up to 200 rows + a `truncated` flag. SQL errors come back as a result (SQLState + message), not an exception. `password_hash` must be unreadable — if the console role can still read it, revoke it in V900 with column grants.
- Activity feed: keyset `WHERE id > ? ORDER BY id LIMIT 200`; the UI polls every 2 s (P1 replaces this with LISTEN/NOTIFY).
- Health: reuse P0 `HealthService`.

**Complexity.** Every list query has `LIMIT` or keyset paging; no N+1 (load roster + marks with joins); `// Complexity:` header on each service class.

## 6. Frontend — `ums-ui` (JavaFX 21, code-built views, no FXML for speed)

- Clean, flat look: one `app.css` (accent colour, 8 px spacing, readable table fonts). Window 1200×760, resizable.
- **Never call the backend on the JavaFX thread.** One helper `Background.run(Supplier<T>, Consumer<T> onOk, Consumer<UmsApiException> onError)` using `javafx.concurrent.Task` on a bounded executor (4 threads); results via the Task's handlers. A spinner shows while running.
- Errors appear in a status bar / alert as the English message from the message key, never a stack trace.
- **Login view**: username, password (`PasswordField`), Sign in. Shows demo credentials hint in DEV/LOCAL only.
- **Shell**: top bar (app name, user, role, Logout) + `TabPane` per role:
  - **Student**: *Register* (open sections table: course, section, credits, day/time, room, seats left, faculty; Register / Drop buttons) · *Timetable* (week grid or sorted table) · *Attendance* (per course %) · *Results* (per course total/grade/GP; SGPA, CGPA).
  - **Faculty**: *My sections* (table) · *Attendance* (section + date picker → roster with Present/Absent/Excused choice → Save) · *Marks* (section → editable table: student × component, Absent checkbox → Save; Submit sheet button showing sheet state).
  - **Admin**: *Term* (current term, state, buttons: Start teaching / Start grading / Publish results — disabled when not allowed) · *Mark sheets* (section, faculty, state; Approve / Return) · *Users* (list + "New user" form: role, username, name; returns a temporary password shown once — Tier 2) · *Activity* (same feed as Dev, read-only).
  - **Dev**: *Activity feed* (auto-refresh 2 s, newest first, time · user · role · action · entity · outcome) · *SQL console* (text area, Run, results `TableView` built from column metadata, row count + time + truncated notice, errors in red) · *Health* (status pill + probe details).

## 7. Seed and run

- `Main --seed-demo` (refuses unless `UMS_ENV` is LOCAL or TEST, and only on an empty `app_user`) creates:
  - users (password for all: **`DemoPass@2026`**): `admin`, `dev`, `fac.rao`, `fac.menon`, `stu.anu`, `stu.binu`, `stu.chitra`, `stu.dev`, `stu.esha`
  - 1 department (CSA), 1 programme (MCA), 4 courses (CS501 Data Structures 4cr, CS502 DBMS 4cr, CS503 Java 3cr, CS504 AI 3cr; CS504 requires CS501)
  - 1 term "Odd 2026" in `REGISTRATION_OPEN`, 4 sections with one slot each, two of them overlapping in time (to demo `TIMETABLE_CLASH`), one with capacity 2 (to demo `SECTION_FULL`), each with components Internal (max 40, weight 40) and Final (max 60, weight 60).
- `README_DEMO.md`: exact commands (create `ums_proto`, env vars, `mvn -q -DskipTests -DskipITs install`, `mvn -pl ums-app exec:java -Dexec.args=--seed-demo`, `mvn -pl ums-ui javafx:run`) and the 6-step click-through script from §1.

## 8. Priority order and time boxes

| Time | Tier | Deliver |
| --- | --- | --- |
| 0–10 min | 1 | Branch, `ums_proto` DB, V900/V901, `ums-ui` module skeleton that opens a window, seed command |
| 10–25 min | 1 | Login + session + role facades; Student register/drop with all checks + timetable; Admin term transitions |
| 25–40 min | 1 | Faculty marks entry + submit; Admin approve + publish; Student results/SGPA/CGPA; activity log writes |
| 40–50 min | 1 | Dev activity feed + SQL console + health; one E2E test + last-seat test (§10) |
| 50–55 min | 2 | Attendance (faculty record, student %); mark sheet return; admin create user |
| 55–60 min | 3 | Prerequisite check, lockout, UI polish; `README_DEMO.md`; final report |

**Cut from Tier 3 first, then Tier 2.** Tier 1 is the demo; finish it even if it takes longer, and say so.

## 9. What this prompt overrides in CLAUDE.md (for this branch only)

- No STOP points; one final report.
- Full `mvn verify` / JaCoCo 80 % / PIT not required; `-Pfast verify` must be green.
- Activity log is synchronous; polling instead of LISTEN/NOTIFY; no feature flags, rate limiting, change history, revisions, notices, reports, backups, privacy.
- Facade methods may be added to `ums-api` on this branch without the full DTO review.

**Still mandatory:** OOP layering (UI → facade → service → repository), `PreparedStatement` only (console excepted), atomic seat update, state machines for term and mark sheet, BCrypt, no secrets in logs, no backend calls on the FX thread, bounded queries.

## 10. Tests (minimum)

- `ProtoTermCycleIT` (in `ums-app/src/test`, against `ums_proto` or a fresh test DB): runs §1 steps 1–5 through the facades and asserts SGPA/CGPA equal hand-calculated values for two students.
- `ProtoLastSeatIT`: 20 threads register for a capacity-1 section → exactly 1 success, 19 `SECTION_FULL`, `enrolled_count = 1`.
- `GradeCalculatorTest`: boundary cases 39.99→F, 40→P, 84.5→A+, 90→O, absent = 0.
- `ConsoleGuardTest`: rejects `UPDATE`, `DROP`, two statements, `SELECT` after a `--` comment is accepted.

## 11. Final report (instead of STOP reports)

```
## PROTOTYPE REPORT — full slice
1. Status — Tier 1 / 2 / 3: done or cut (one line each); total time taken
2. How to run — the 4 commands
3. Demo walkthrough — each §1 step: works / partly / not, with any known issue
4. Tests — the 4 tests and results; `-Pfast verify` result
5. Shortcuts taken — everything simplified vs the production design
6. What P1–P5 can reuse — classes worth keeping, and what must be rebuilt
7. Commits — hash + message
```

Push `prototype/full-slice` to GitHub (no PR). Do not merge anything.
