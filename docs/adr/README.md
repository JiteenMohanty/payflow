# Architecture Decision Records

ADRs are immutable once accepted. A changed decision gets a **new** ADR that supersedes the old one (mark the old one's status as `Superseded by ADR-0NN`); we do not edit history in place.

| ID | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular Monolith over Microservices | Accepted |
| [0002](0002-kafka-event-streaming.md) | Kafka for Event Streaming | Accepted |
| [0003](0003-postgresql.md) | PostgreSQL as Primary Datastore | Accepted |
| [0004](0004-redis.md) | Redis for Idempotency Cache & Rate Limiting | Accepted |
| [0005](0005-transactional-outbox.md) | Transactional Outbox Pattern | Accepted |
| [0006](0006-provider-abstraction.md) | Provider Abstraction via Adapter Interface | Accepted |
| [0007](0007-idempotency-strategy.md) | Stripe-style Idempotency Key Design | Accepted |
| [0008](0008-double-entry-ledger.md) | Immutable Double-Entry Ledger | Accepted |
| [0009](0009-multi-tenancy-strategy.md) | Multi-Tenancy via Row-Level Organization Scoping | Accepted |
| [0010](0010-authentication-strategy.md) | API Keys (merchants) + JWT (dashboard) | Accepted |
| [0011](0011-webhook-reconciliation.md) | Webhooks as Reconciliation Source of Truth | Accepted |
| [0012](0012-observability-stack.md) | Observability Stack Selection | Accepted |
| [0013](0013-deployment-architecture.md) | Deployment Architecture | Accepted |

## Template

```markdown
# ADR-00NN: Title

**Status:** Proposed | Accepted | Superseded by ADR-00MM
**Date:** YYYY-MM-DD

## Context
What problem forces this decision. What constraints apply.

## Decision
What we're doing, stated plainly.

## Alternatives Considered
What else was on the table and why it lost.

## Consequences
What this makes easier, what it makes harder, what it forecloses.
```
