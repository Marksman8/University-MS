# ADR 010 — PostgreSQL 18 as the single target

- Status: Accepted
- Date: 2026-10-10
- Source: owner ruling at the Steps 3–5 review; commit `054af4a`

## Context

The specs originally said PostgreSQL 16; the development machine runs PostgreSQL 18. Supporting
several major versions would multiply test matrices for no benefit to one institution.

## Decision

PostgreSQL 18 everywhere: local development, integration tests (throwaway cluster via
`scripts/dev/test-cluster.*`), CI (`postgres:18` service) and production. Flyway 13.10 and the
pgjdbc driver 42.7 are verified against 18.6.

## Consequences

- One version to test and document; PostgreSQL 18 features may be used.
- Production must run 18 before go-live; older servers are unsupported.
- `docs/design/*.md` are owner-maintained and updated separately.

## Alternatives

- **PostgreSQL 16 as specified originally** — would require installing an older server locally.
- **Support 16 and 18** — doubled CI time, no user benefit.
