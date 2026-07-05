# ADR-0012: Observability Stack Selection

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow is a financial system with asynchronous machinery (outbox relay, webhook retries, scheduled jobs, provider reconciliation) where correctness bugs manifest as silent backlogs or drift rather than crashes — "the outbox has 40,000 pending rows" or "12% of payments are stuck in AUTHORIZED past their expected window" are the failure signatures, not stack traces. The system needs to be observable by default, not instrumented reactively after an incident.

## Decision

Adopt the full stack named in the product requirements as complementary layers, not redundant choices:

- **Micrometer** as the metrics facade inside the application (vendor-neutral instrumentation API).
- **Prometheus** to scrape and store metrics; **Grafana** for dashboards over them.
- **OpenTelemetry** for distributed tracing instrumentation, **Jaeger** as the trace backend/UI — critical for following a single payment's request across PayFlow core → Mock Provider → back via webhook, which is exactly the kind of cross-process flow a stack trace can't show.
- **Structured JSON logging** with **correlation IDs** propagated via MDC through provider calls and Kafka headers, so every log line for a given request (or a given payment, across its whole async lifecycle) can be correlated even when it spans the outbox/Kafka/webhook boundary.
- Custom business metrics beyond framework defaults: payments by status, outbox backlog size, webhook delivery success/failure/DLQ rate, idempotency replay rate, reconciliation sweep findings — because "the HTTP server is healthy" says nothing about "is the ledger correct."

## Alternatives Considered

- **Logs only, no metrics/tracing**: rejected — asynchronous, multi-hop flows (outbox → Kafka → webhook dispatch → merchant) are exactly the case where grepping logs across processes doesn't scale; tracing and aggregate metrics answer questions logs can't ("what's the p99 webhook delivery latency" is a metrics question, not a log question).
- **A hosted all-in-one observability SaaS instead of self-hosted Prometheus/Grafana/Jaeger**: rejected for v1 — the product requirements name the OSS stack explicitly, and self-hosting keeps the deployment story (Docker Compose on Hetzner/DigitalOcean) self-contained without an external vendor dependency at this stage.
- **Only technical metrics (CPU, memory, HTTP latency), no business metrics**: rejected — for a payment orchestrator, "payments stuck in AUTHORIZED > 1 hour" is a more urgent signal than CPU usage, and needs to be a first-class dashboard panel, not something inferred later from logs.

## Consequences

- Every module that does anything asynchronous (outbox, webhook, scheduled jobs) must emit metrics for its backlog/failure state as part of that module's definition of done — not an afterthought bolted on during M10.
- Correlation IDs must be threaded through the provider HTTP client and Kafka producer/consumer headers from the first implementation of each, since retrofitting correlation propagation after the fact means re-touching every call site.
- Running Prometheus + Grafana + Jaeger + an OTel Collector locally means the Docker Compose dev environment is heavier than a minimal API-only setup — accepted deliberately so the observability story is exercised in development, not only in production where it's harder to validate.
- Grafana dashboards and alert thresholds (e.g. outbox backlog size, webhook DLQ rate) are part of the M10 deliverable, versioned as code (provisioned dashboards), not clicked together manually and lost.
