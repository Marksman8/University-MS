# ADR 009 — Keyset paging only

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §5, §13, CLAUDE.md §2.2, P0 Step 6, STOP B rulings

## Context

Lists (activity log, students, marks, notices) grow without bound. `OFFSET` paging costs O(n) per
page and returns duplicates or gaps when rows are inserted between pages.

## Decision

Every list uses keyset paging on an indexed `(sort_key…, id)`: `BaseRepository.queryPage` with a
`KeysetQuery` adds a row-comparison seek predicate (`(k1, k2) < (?, ?)`) and orders by the same
keys, all in one direction, with the last key unique. It reads `size + 1` rows to know whether a
next page exists. Page size is 1..200 (`PageRequest`). The cursor is opaque: URL-safe Base64 of a
JSON array of the last row's key values (`CursorCodec`, ≤ 512 characters); a tampered cursor is
`VALIDATION_FAILED`. `OFFSET` is forbidden.

## Consequences

- Each page costs O(log n + p) with the right index; memory O(p).
- Pages are stable under concurrent inserts (no duplicates, no gaps), proven by an IT over 1,000
  rows with heavily duplicated sort keys.
- No "jump to page N"; the UI offers next/previous only.

## Alternatives

- **OFFSET/LIMIT** — forbidden: linear cost and unstable pages.
- **Server-side cursors** — hold transactions across user think-time (forbidden by ADR 006).
