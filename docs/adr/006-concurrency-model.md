# ADR 006 — READ COMMITTED, conditional updates, global lock order, optimistic versions, retry

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §8, §9, CLAUDE.md §2.5, P0 Steps 6 and 8, STOP B/C rulings

## Context

Registration opens to many students at once; mark sheets and results are edited by several roles.
The system needs correctness under contention without long locks or user think-time transactions.

## Decision

- **Isolation:** READ COMMITTED; one transaction per facade call (`TransactionInterceptor` →
  `TransactionRunner`, propagation REQUIRED, thread-bound). `@Writes` methods run read-write,
  others read-only; a write inside a read-only transaction is `INTERNAL_ERROR`.
- **Conditional updates:** counters change only through `UPDATE … WHERE condition`
  (e.g. `enrolled_count < capacity`), never read-then-write in Java.
- **Global lock order:** term → student → section → mark sheet → result.
- **Optimistic versions:** mutable aggregates carry `version`; state changes use compare-and-set;
  0 rows → `StaleDataException` (`STALE_DATA`).
- **Timeouts:** statement 10 s (console 15 s), lock wait 5 s, pool wait 5 s.
- **Retry:** SQLState 40001/40P01 → `ConflictException`, retried by `RetryPolicy` with uniform
  20–100 ms back-off, **3 attempts in total (2 retries)**, only around the outermost transaction —
  owned by `TransactionRunner`, so a joined (inner) call never retries on its own. Exhausted →
  `CONFLICT_TRY_AGAIN`.

## Consequences

- Deadlocks are avoided by ordering; the rare serialisation failure is retried transparently.
- Facades stay stateless and thread-safe; shared in-memory state uses concurrent structures only.
- Every concurrency-sensitive feature needs a `ConcurrencyHarness` test (50 repeats nightly).

## Alternatives

- **SERIALIZABLE** — simpler reasoning, more aborts under registration load.
- **Pessimistic locking of whole aggregates** — throughput collapse at registration peaks.
- **Retrying at every nesting level** — multiplicative retries; rejected (STOP C ruling).
