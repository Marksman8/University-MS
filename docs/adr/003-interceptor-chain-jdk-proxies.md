# ADR 003 — Interceptor chain over JDK dynamic proxies

- Status: Accepted
- Date: 2026-10-10
- Source: master prompt §4, P0 Step 8, STOP C rulings

## Context

Every facade call needs the same cross-cutting behaviour in a fixed order: error mapping, session,
authorisation, feature flags, rate limits, tracing, audit, transactions. Spring/AOP is banned
(CLAUDE.md §9), and hand-writing this in every facade method would drift.

## Decision

`FacadeProxyFactory` wraps each facade implementation in a JDK dynamic proxy that runs an
interceptor chain (Proxy + Chain of Responsibility). Slots are a fixed enum, outermost first:
`ERROR_MAPPING → SESSION → AUTHORISATION → FEATURE_FLAG → RATE_LIMIT → TRACING → AUDIT →
TRANSACTION`. The chain is sorted once at creation and duplicate slots are rejected.
`Invocation.proceed()` is positional, so an interceptor may call it again (retry) safely.
`equals/hashCode/toString` bypass the chain. `Invocation` exposes only the declaring type, method
name and annotations (no mutable `Method`). Method annotations (`@Writes`, `@Audited`, `@Feature`,
`@RateLimited`) drive behaviour declaratively.

## Consequences

- Cross-cutting rules live in one place each, testable in isolation.
- Overhead is O(k) per call with k ≤ 8 links.
- Only interface methods can be intercepted; facades are interfaces by construction.
- Interceptors may live only in `edu.ums.platform.invoke` (ArchUnit rule).

## Alternatives

- **Spring AOP / AspectJ** — banned dependency; bytecode weaving complicates the build.
- **Hand-written decorators per facade** — repetition and inconsistent ordering.
- **Bytecode-generated proxies (ByteBuddy)** — not on the approved list; JDK proxies suffice.
