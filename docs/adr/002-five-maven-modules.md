# ADR 002 — Five Maven modules; the frontend depends only on `ums-api`

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §4 (Modules), CLAUDE.md §3, P0 Step 1

## Context

A modular monolith needs boundaries that the compiler enforces, not just conventions. The frontend
must not reach internal classes, and shared test infrastructure must not create build cycles.

## Decision

Five modules in reactor order:

| Module | Contents | Depends on |
| --- | --- | --- |
| `ums-api` | facades, DTOs, typed ids, `ErrorCode`, `UmsApiException`, annotations | nothing |
| `ums-platform` | config, db, interceptors, errors, observe, state machine, concurrency | api |
| `ums-testkit` | test infrastructure (test scope only) | api, platform |
| `ums-core` | feature packages, facade implementations, migrations | api, platform (+ testkit for tests) |
| `ums-app` | `AppContext`, `UmsRuntime`, `Main`; acceptance/E2E tests | all (+ testkit for tests) |

The frontend depends only on `ums-api`. ArchUnit rules (`ArchitectureRules`) and the enforcer back
this up.

## Consequences

- `ums-api` stays dependency-free (checked by `API_DEPENDS_ON_NOTHING`).
- Tests that need the testkit cannot live in `ums-api` or `ums-platform` (they are upstream of
  it); platform database ITs therefore live in `ums-testkit/src/test` and the platform keeps its
  coverage with unit tests (STOP D, Step 12).
- Wiring happens in exactly one place (`ums-app`).

## Alternatives

- **Single module with package conventions** — boundaries erode silently.
- **Testkit depending on core** — reactor cycle, because core's tests depend on the testkit.
