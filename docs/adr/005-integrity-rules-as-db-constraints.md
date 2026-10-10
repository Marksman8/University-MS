# ADR 005 — Integrity rules as database constraints

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §6, §10

## Context

Rules such as "a section never exceeds its capacity", "one live enrolment per course and term",
"no room or faculty double-booking" and "one active term" must hold under concurrency. Checks in
Java alone race between read and write.

## Decision

Hard rules are database constraints, named exactly as in master §6:
`ck_section_capacity`, `uq_enrolment_live` (partial unique), `ex_room_slot` and `ex_faculty_slot`
(GiST exclusion via `btree_gist`), `uq_one_active_term` (partial unique on a constant),
`uq_result_current`, `ck_mark_nonneg`. Services still check rules first to return precise reason
codes, but the constraint is the final authority. `SqlErrorMapper` maps violations to error codes
via `ConstraintErrorMap` (see ADR 012).

## Consequences

- Correctness holds even with concurrent writers or a buggy code path.
- Constraint names become part of the contract between schema and code (ADR 012).
- Some rules exist twice (service pre-check and constraint); the constraint wins.

## Alternatives

- **Java-only validation** — racy without serialisable isolation or heavy locking.
- **SERIALIZABLE isolation everywhere** — more retries and contention than targeted constraints.
