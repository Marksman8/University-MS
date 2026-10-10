# ADR 007 — Generic StateMachine with compare-and-set persistence

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §7, CLAUDE.md §2.3, P0 Step 9, STOP C rulings

## Context

Seven lifecycles (term, mark sheet, enrolment, account, session, attendance session, privacy
request) have guarded transitions and side effects. Ad-hoc `if (state == …)` code scatters the
rules and is hard to test exhaustively.

## Decision

One generic `StateMachine<S extends Enum<S>, C>` in `ums-platform`, built with a builder and
backed by `EnumMap<S, EnumMap<S, Transition>>` (O(1) lookup). A transition is a `Guard<C>` (returns
the sealed `GuardResult`: `Pass` or `Fail(ErrorCode, details)`) plus an optional `Action<C>`.
`fire(current, target, ctx)` throws `InvalidTransitionException` for undefined pairs and
`GuardFailedException` when the guard refuses, then runs the action. The machine is immutable and
stateless; the caller keeps the state and persists it with `BaseRepository.casState`:
`UPDATE … SET state=?, version=version+1 WHERE id=? AND state=? AND version=?` (0 rows →
`STALE_DATA`).

## Consequences

- Every machine is testable exhaustively: one test per (from, to) pair plus a jqwik property that
  random sequences only take allowed transitions (≥ 1,000 tries).
- Concurrent transitions are safe: the losing writer gets `STALE_DATA`.
- Guards must be side-effect free; side effects belong in actions.

## Alternatives

- **Per-entity hand-written transitions** — duplication, uneven testing.
- **Stateful machine objects** — would need synchronisation and could disagree with the database.
- **State-machine libraries** — not on the approved dependency list.
