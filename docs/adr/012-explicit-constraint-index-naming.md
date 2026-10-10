# ADR 012 — Explicit constraint and index names as a contract for `SqlErrorMapper`

- Status: Accepted
- Date: 2026-10-10
- Source: STOP B ruling 1; master prompt §6, §10

## Context

`SqlErrorMapper` turns integrity violations into precise error codes by looking up the constraint
name the server reports (`PSQLException.getServerErrorMessage().getConstraint()`). PostgreSQL's
generated names (e.g. `app_user_username_key`) depend on table and column names and change
silently on refactoring, which would turn a precise error into `INTERNAL_ERROR`.

## Decision

Every constraint and index is named explicitly in migrations with a prefix: `pk_`, `fk_`, `uq_`,
`ck_`, `ex_`, `idx_`. Names used for error mapping are registered once in `ConstraintErrorMap`:

| Constraint | ErrorCode |
| --- | --- |
| `uq_enrolment_live` | `ALREADY_ENROLLED` |
| `uq_app_user_username` (declared in P1 as `CONSTRAINT uq_app_user_username UNIQUE (username)`) | `USERNAME_TAKEN` |
| `ex_room_slot`, `ex_faculty_slot` | `SLOT_CLASH` |
| `ck_section_capacity` | `SECTION_FULL` |
| `uq_one_active_term` | `GUARD_FAILED` |
| `uq_result_current` | `STALE_DATA` |
| `ck_mark_nonneg` | `MARK_OUT_OF_RANGE` |

Driver error details are hidden (`logServerErrorDetail=false`) so row values never reach logs;
constraint names and SQLStates still do.

## Consequences

- Renaming a constraint is a breaking change that needs a matching `ConstraintErrorMap` update.
- `BaselineMigrationIT` checks every constraint and index name against the prefix rule from the
  catalog.
- The pinned `activity_log` definition was amended with names (`pk_activity_log`,
  `ck_activity_log_role`, `ck_activity_log_outcome`).

## Alternatives

- **Rely on generated names** — brittle mapping.
- **Parse error message text** — locale- and version-dependent; leaks row values.
