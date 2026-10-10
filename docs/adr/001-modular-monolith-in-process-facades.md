# ADR 001 — Modular monolith with in-process facades

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §1, §4, §5 (DESIGN.md §Decision records once exported)

## Context

UMS serves one institution with four roles (Student, Faculty, Admin, Dev). The JavaFX frontend is
built separately by the owner and runs in the same JVM. Load targets are modest (p95 < 200 ms at
10,000 students). The team is small, and operations must stay simple on Windows and Fedora.

## Decision

Build one deployable JVM process: a modular monolith. The frontend calls the backend only through
four role facades (`StudentApi`, `FacultyApi`, `AdminApi`, `DevApi`) reached from `UmsClient` →
`Session`. Calls are plain blocking Java method calls with immutable DTOs and typed errors
(`UmsApiException`). There is no network API.

## Consequences

- No serialisation, HTTP stack, service discovery or distributed transactions; one transaction per
  facade call is easy to guarantee.
- Facades are the only contract the frontend sees, so the backend can change freely behind them.
- Calls block and must not run on the JavaFX thread (documented on `UmsClient`/`Session`).
- Scaling is vertical; splitting into services later means turning facades into remote APIs.

## Alternatives

- **REST/gRPC microservices** — operational cost, network failure modes and distributed
  consistency far beyond the need of one institution.
- **Frontend talking to the database directly** — no single place for authorisation, auditing,
  validation or transactions.
