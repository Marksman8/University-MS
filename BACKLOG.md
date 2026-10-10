# Backlog

Ideas and follow-ups that are out of scope for the current phase. Nothing here is built without review.

| # | Item | Raised in | Notes |
| --- | --- | --- | --- |
| 1 | Allow DDL in the Dev console | Owner request (pre-P0) | Conflicts with master §2 DEV "no DDL". Needs owner decision + ADR before P6. |
| 2 | 2026-10-10 — P1: `ErrorReporter` writes uncaught errors to `app_error_log` via the audit-writer pattern (bounded queue, best-effort), replacing the no-op `UncaughtErrorSink`. | STOP C review | `UncaughtErrorSink` stays a no-op in P0. |
