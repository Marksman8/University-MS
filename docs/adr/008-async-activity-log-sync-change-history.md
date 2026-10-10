# ADR 008 — Asynchronous activity log plus synchronous change history

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §2 (AUDIT), §4 (side channels), §12; P0 Step 7

## Context

Two kinds of audit data exist. The activity log records every write, login, logout, failure and
denial; it is high-volume and must never slow down or fail the user's action. Change history
(before/after for marks, attendance, results, eligibility, roles) is evidence that must be exactly
consistent with the change it describes.

## Decision

- **Activity log** (`activity_log`): written asynchronously. Facade calls enqueue into a bounded
  queue (10,000) in O(1); one `audit-writer` thread batch-inserts (≤ 100 rows or every 500 ms).
  Best-effort: a full queue drops and counts the event (drops > 0 raise an alert). An `AFTER
  INSERT` trigger sends `NOTIFY ums_activity` with the row id; the Dev live feed listens through a
  dedicated least-privilege connection (`ums_listener`, ADR 011). Roles include `SYSTEM` and
  `ANONYMOUS` for non-user actions.
- **Change history** (`change_history`): written synchronously in the same transaction as the
  change, so it commits or rolls back with it.

## Consequences

- User latency is unaffected by audit volume; a crash can lose at most the queued activity
  events, never change history.
- The writer and listener threads join the shutdown sequence (drain audit ≤ 5 s first).
- P0 ships the tables, trigger and roles; the writer and listener arrive in P1.

## Alternatives

- **Everything synchronous** — audit volume on the user's critical path.
- **Everything asynchronous** — change history could diverge from the data it documents.
- **External log pipeline** — extra infrastructure for one institution.
