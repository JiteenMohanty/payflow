# PayFlow

**PayFlow is a Payment Orchestration Platform.** It is not a payment gateway, and it does not process card data.

PayFlow sits between merchants and payment providers (Stripe, Razorpay, Adyen, PayPal — currently backed by a Mock Provider for development and testing) and owns the parts of a payment's life that shouldn't have to be rebuilt per provider: a stable merchant-facing API, the payment lifecycle state machine, idempotent request handling, an immutable double-entry ledger, and reliable webhook delivery in both directions.

> **Status: Phase 0 — Design.** The engineering design has been completed and is pending sign-off. No application code exists yet; see [`docs/EDD.md`](docs/EDD.md) for the full blueprint and [§11 Implementation Roadmap](docs/EDD.md#11-implementation-roadmap) for what ships when. This README will grow screenshots, a quick start, and deployment instructions as milestones land.

## Why a platform, not a gateway

A payment gateway processes a charge. An orchestration platform sits above one or more gateways and owns the things merchants otherwise have to build themselves every time they add a second provider: one consistent API and webhook contract, idempotent retries that are actually safe, a ledger that doesn't drift, and reconciliation when a provider's synchronous response and its asynchronous webhook disagree. See [ADR-0011](docs/adr/0011-webhook-reconciliation.md) for why that reconciliation model is the core of what PayFlow does, not a side feature.

## Architecture at a glance

- **Modular Monolith** (Java 21 / Spring Boot 3) — see [ADR-0001](docs/adr/0001-modular-monolith.md) for why this beats microservices at PayFlow's current scale.
- **PostgreSQL** system of record, **Redis** for idempotency caching and rate limiting, **Kafka** as the event backbone fed by a **Transactional Outbox**.
- A separate **Mock Provider service** that behaves like a real external provider — its own latency, failures, retries, and signed async webhooks — so the provider abstraction ([ADR-0006](docs/adr/0006-provider-abstraction.md)) is exercised honestly, not against a convenient fake.
- Full observability by default: Micrometer, Prometheus, Grafana, OpenTelemetry, Jaeger, structured JSON logs with correlation IDs.
- A React 18 / TypeScript admin dashboard for organizations to view payments, ledger entries, webhook history, and issue refunds.

Full architecture (C4 diagrams, database schema, API contracts, event flows, sequence diagrams, and all 12 ADRs) lives in [`docs/EDD.md`](docs/EDD.md) and [`docs/adr/`](docs/adr/README.md).

## Repository Structure

```
payflow/
├── backend/            # Maven reactor: payflow-core (the monolith) + mock-provider-service
├── frontend/            # React admin dashboard
├── infra/               # Docker Compose, Nginx, Prometheus, Grafana configs
├── docs/                # EDD, ADRs, diagrams
├── CHANGELOG.md
└── README.md
```

## Quick Start

Not yet available — the backend and frontend are unimplemented (Phase 0). Once M0 (bootstrap & tooling) lands, this section will cover `docker compose up` for local development.

## Roadmap

See [§11 Implementation Roadmap](docs/EDD.md#11-implementation-roadmap) in the EDD for the full milestone sequence (M0 bootstrap through M14 release polish), and [§14 Future Extensibility](docs/EDD.md#14-future-extensibility) for what's designed-in but deliberately out of scope for v1 (real provider integrations, disputes, subscriptions, multi-currency settlement, platform fees).

## Documentation

- [Engineering Design Document](docs/EDD.md) — architecture, schema, API contracts, event flows, diagrams, roadmap, risk analysis
- [Architecture Decision Records](docs/adr/README.md)
- [Changelog](CHANGELOG.md)
