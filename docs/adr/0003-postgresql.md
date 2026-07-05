# ADR-0003: PostgreSQL as Primary Datastore

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow's core data — payments, refunds, ledger entries, outbox events, idempotency keys — is relational, transactional, and requires strong consistency: a capture, its ledger postings, and its outbox event must commit together or not at all, and the ledger must support a `SELECT ... FOR UPDATE` row lock plus a check constraint that a transaction's entries net to zero. This needs ACID transactions and real constraints, not eventual consistency.

## Decision

Use PostgreSQL 16 as the single system of record for all modules, managed with Flyway migrations. `jsonb` columns are used for genuinely schemaless data (webhook payloads, event payloads, metadata) while every financial and state field is a typed column with constraints.

## Alternatives Considered

- **MongoDB or another document store**: rejected. No native multi-row ACID transaction across the payment/ledger/outbox write in the way Postgres gives for free, and no equivalent to a `CHECK` constraint enforcing ledger balance invariants at the database level — those guarantees would have to be reimplemented in application code, which is strictly worse for a financial ledger.
- **MySQL**: rejected in favor of Postgres for richer constraint/index support (partial indexes, `FOR UPDATE SKIP LOCKED` for outbox polling, native `jsonb` with indexing) that map directly onto the outbox and idempotency access patterns.
- **Separate databases per module**: rejected — see [ADR-0001](0001-modular-monolith.md); a single transactional boundary across payment/ledger/outbox is the point.

## Consequences

- Capture → ledger posting → outbox event is one `@Transactional` unit backed by real ACID guarantees, not application-level coordination.
- Flyway migrations are additive-only after v1.0 (no destructive schema changes without a documented migration path), which is a discipline the team must maintain, not something the database enforces for us.
- `FOR UPDATE SKIP LOCKED` is used by the outbox poller to safely run multiple poller instances without double-publishing — a Postgres-specific mechanism the design leans on directly.
- Ledger integrity (entries per transaction net to zero) is enforced by a DB check constraint in addition to application logic — defense in depth for the one dataset where "the app forgot to check" is unacceptable.
