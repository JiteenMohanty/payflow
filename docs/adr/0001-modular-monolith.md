# ADR-0001: Modular Monolith over Microservices

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow orchestrates payments across `payment`, `refund`, `ledger`, `webhook`, `provider`, `merchant`, `organization`, and `outbox` concerns. These are highly transactional and consistency-sensitive: a captured payment, its ledger entries, and its outbox event must commit atomically or not at all. The team building this is small (effectively one engineering team, not eight).

Microservices would force either distributed transactions (sagas) across payment/ledger/outbox from day one, or an eventual-consistency model for operations that are naturally atomic (a capture and its ledger posting are the same fact, not two facts that reconcile later). That complexity buys nothing at current scale and actively works against correctness guarantees a payments product needs.

## Decision

Build PayFlow core as a single Spring Boot deployable — a **Modular Monolith**. Modules are separated by Java package with enforced boundaries (ArchUnit), communicate through public interfaces and domain events, and share one PostgreSQL database with one transaction manager. The Mock Provider is the one deliberate exception: it ships as a separate deployable because it must behave like an external system (its own latency/failure/webhook signing), not because of a service-boundary ideology.

## Alternatives Considered

- **Microservices per module** (`payment-service`, `ledger-service`, `webhook-service`, ...): rejected. Would require sagas or eventual consistency for the capture→ledger→outbox atomic unit, adds operational overhead (N deployables, N databases or a shared DB anti-pattern, service mesh, distributed tracing to debug what used to be a stack trace) with no corresponding benefit at this team size or load.
- **Single unstructured Spring Boot app** (no module boundaries): rejected. Without enforced boundaries, a monolith degrades into a ball of mud as it grows past a few modules — the modular structure is what makes a later extraction (if ever needed) possible without a rewrite.

## Consequences

- One deployment unit, one database transaction scope — capture, ledger posting, and outbox event insert are a single `@Transactional` boundary. This is the main win: correctness by construction, not by reconciliation.
- Module boundaries are enforced by tooling (ArchUnit), not just convention, so the monolith doesn't quietly become a ball of mud as modules are added.
- If a specific module (e.g. `webhook` dispatch, which is CPU/IO-bound and independently scalable) later needs to scale independently, its interface-based boundary makes extraction a targeted refactor, not a rewrite.
- Horizontal scaling of PayFlow core scales the whole monolith together; this is an acceptable tradeoff until a specific module's load profile diverges enough to justify extraction — a decision that would get its own ADR when it happens.
