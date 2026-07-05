# ADR-0005: Transactional Outbox Pattern

**Status:** Accepted
**Date:** 2026-07-06

## Context

A payment capture must both commit to Postgres and eventually reach Kafka so downstream consumers (merchant webhook dispatch, reconciliation, analytics) learn about it. Writing to the database and publishing to Kafka are two separate systems with no shared transaction — a naive "commit DB, then publish to Kafka" sequence has a window where the DB commit succeeds and the process crashes before publishing, silently losing the event with no record it was ever supposed to happen.

## Decision

Implement the Transactional Outbox Pattern: every state-changing operation writes its domain row **and** an `outbox_events` row (`status=PENDING`) in the same database transaction. A separate `@Scheduled` `OutboxPublisher` polls `PENDING` rows (`FOR UPDATE SKIP LOCKED` for safe concurrent pollers), publishes to the appropriate Kafka topic, and marks `PUBLISHED` on success or increments `retry_count` on failure. Rows that exceed `max_retries` move to `dead_letter_events` for operator visibility rather than retrying forever.

## Alternatives Considered

- **Direct publish inside the request thread (DB commit then Kafka send)**: rejected — this is exactly the dual-write problem described above; a crash or Kafka unavailability between the two steps loses the event with no trace.
- **Change Data Capture (Debezium) on the WAL**: considered as a more "automatic" outbox relay. Rejected for v1 as an added operational dependency (Kafka Connect + Debezium + WAL replication slot management) that isn't justified before the simpler polling publisher is proven to be a bottleneck; the outbox table shape is intentionally CDC-compatible if we adopt this later without a schema change.
- **Two-phase commit between Postgres and Kafka**: rejected. Kafka doesn't participate in XA transactions in a way that's operationally sane here, and 2PC has its own well-known failure modes (coordinator crash) that outweigh the outbox's simplicity.

## Consequences

- Every event PayFlow ever needs to have "definitely happened" is guaranteed to reach Kafka eventually (at-least-once), at the cost of publish latency being decoupled from request latency (the polling interval, ~500ms) rather than synchronous.
- Consumers must be idempotent (defense in depth against at-least-once redelivery), which is already required regardless per [ADR-0002](0002-kafka-event-streaming.md).
- The outbox table itself becomes an audit log of "everything PayFlow decided happened," which is useful for debugging and reconciliation independent of Kafka.
- Requires operational monitoring of outbox backlog size and `dead_letter_events` count as first-class metrics (see [ADR-0012](0012-observability-stack.md)) — a growing backlog is a leading indicator of a Kafka or downstream problem before anything else notices.
