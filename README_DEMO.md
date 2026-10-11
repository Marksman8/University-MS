# UMS full-slice prototype — demo guide

> Branch `prototype/full-slice` only. Database `ums_proto`. Never merged into `main`.
> Demo data only: every demo user's password is `DemoPass@2026`; `--seed-demo` refuses to run
> unless `UMS_ENV` is `LOCAL` or `TEST`, and only on an empty `app_user` table.

## 1. Create the database (once)

With the local test cluster (`scripts/dev/test-cluster.ps1`, PostgreSQL 18 on port 55432):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 init -Database ums_proto
```

This creates `ums_proto` and applies `scripts/db/create_roles.sql` (roles `ums_owner`, `ums_app`,
`ums_dev_console`, `ums_listener`, `ums_backup`, schema `ums`). Migrations V1, V2, V900 and V901
run automatically at startup.

## 2. Environment (PowerShell)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot'   # any JDK 21
$env:UMS_ENV = 'LOCAL'
$env:UMS_DB_URL = 'jdbc:postgresql://localhost:55432/ums_proto'
$env:UMS_DB_PASSWORD = 'it-app'
$env:UMS_CONSOLE_DB_PASSWORD = 'it-console'
$env:UMS_LISTENER_DB_PASSWORD = 'it-listener'
$env:UMS_OWNER_DB_PASSWORD = 'it-owner'
$env:UMS_BACKUP_DB_PASSWORD = 'it-backup'
```

(These are the test cluster's local-only role passwords.)

## 3. Build, seed, run

```powershell
mvn -q -DskipTests -DskipITs install
mvn -pl ums-app exec:java "-Dexec.args=--seed-demo"
mvn -pl ums-ui javafx:run
```

To start again from scratch, drop and re-create the database (step 1 after
`DROP DATABASE ums_proto WITH (FORCE)`), then seed again.

## Seeded data

| What | Details |
| --- | --- |
| Users | `admin`, `dev`, `fac.rao`, `fac.menon`, `stu.anu`, `stu.binu`, `stu.chitra`, `stu.dev`, `stu.esha` |
| Org | Department CSA, programme MCA |
| Courses | CS501 Data Structures 4 cr · CS502 DBMS 4 cr · CS503 Java 3 cr · CS504 AI 3 cr (requires CS501) |
| History | "Even 2026", RESULTS_PUBLISHED: every student took CS501 (anu A+, binu A, chitra B+, dev B, esha **F**) |
| Current term | "Odd 2026", REGISTRATION_OPEN |
| Sections | CS501-A Mon 10:00–11:30 (rao) · CS502-A Mon 09:00–10:30 (menon) · CS503-A Tue 09:00–10:30, **capacity 2** (rao) · CS504-A Wed 14:00–15:30 (menon) |
| Components | every section: Internal (max 40, weight 40) + Final (max 60, weight 60) |

CS501-A overlaps CS502-A, so it demonstrates `TIMETABLE_CLASH`. CS503-A demonstrates `SECTION_FULL`
once two students hold a seat. stu.esha shows `PREREQ_NOT_MET` on CS504.

## Click-through (the 6 demo steps)

1. **Student**: sign in as `stu.anu`. On *Register*, select and register CS502-A, CS503-A and
   CS504-A. Then try CS501-A: the status bar shows the timetable-clash message with the clashing
   section. Open *Timetable*. Sign out, sign in as `stu.binu`, register CS502-A and CS503-A. Then
   sign in as `stu.chitra`: CS503-A fails with "section full", so register CS502-A instead.
2. **Admin**: sign in as `admin`. On *Term*, click **Start teaching**. *Mark sheets* now lists four
   DRAFT sheets.
3. **Faculty**: sign in as `fac.menon`. *My sections* lists CS502-A and CS504-A. On *Attendance*,
   pick CS502-A and a date (today or earlier), click **Load roster**, set statuses, then **Save
   attendance**. On *Marks*, pick a section, click **Load sheet**, enter Internal/Final values (or
   tick Absent), then **Save marks** and **Submit sheet**. Do the same for every section as
   `fac.rao`, including the empty CS501-A sheet, which can be submitted straight away.
4. **Admin**: click **Start grading**. Approve every sheet on *Mark sheets*. **Publish results** is
   refused with the list of unapproved sections until all sheets are approved.
5. **Student**: sign in as `stu.anu`. *Results* shows total %, grade and grade point per course,
   plus SGPA, CGPA (including the history term) and credits earned. *Attendance* shows the
   percentage per course.
6. **Dev**: sign in as `dev`. *Activity feed* auto-refreshes every 2 s and lists every login,
   logout, registration (including failures), term transition, attendance, marks and approval
   step. In the *SQL console*, try `SELECT * FROM term_result`.
   `SELECT password_hash FROM app_user` comes back as a permission error (42501), and `DELETE …`
   is rejected by the guard. *Health* shows UP.
