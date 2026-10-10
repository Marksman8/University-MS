# ADR 004 — Plain JDBC, no ORM

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §3, §8, CLAUDE.md §2.2 and §3, P0 Step 6

## Context

The core flows (registration seat allocation, mark-sheet approval, result publishing) depend on
precise SQL: row locks in a global order, conditional updates, set-based aggregation and keyset
paging. Hibernate/JPA is banned, and hidden lazy loading would hide N+1 queries.

## Decision

Repositories use JDBC through `BaseRepository` (Template Method): `queryOne`, `queryOptional`,
`queryList` (bounded, ≤ 10,000 rows), keyset `queryPage`, `update`, `updateExactlyOne`, `batch`
(≤ 1,000 items) and `casState`. Only `PreparedStatement`s; every value is bound; the few
concatenated identifiers are validated by a regex-free check. Connections come from HikariCP and
always belong to the thread's transaction (`TransactionRunner`); there is no auto-commit path.
Every `SQLException` goes through `SqlErrorMapper`.

## Consequences

- SQL is explicit and reviewable; performance characteristics are visible.
- Mapping rows to records is manual (`RowMapper`), a small, accepted cost.
- `java.sql` is confined to `..repository..` and `edu.ums.platform.db..` (ArchUnit), with
  `SqlErrorMapper` as the documented exemption.

## Alternatives

- **Hibernate/JPA** — banned; poor fit for lock-ordered, conditional, set-based SQL.
- **jOOQ / MyBatis** — not approved; adds a layer the template methods already cover.
