# ADR-0002: Kafka for Event Streaming

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow needs to fan a single domain fact (e.g. "payment captured") out to multiple independent consumers: the merchant webhook dispatcher today, and later a reconciliation sweeper, analytics projector, and potentially other providers' settlement processes. These consumers need their own offsets/retry semantics — a webhook delivery failure retrying for an hour must not block or duplicate work for analytics. The events must also survive a consumer being down, since a merchant's webhook infrastructure being offline for an hour is normal, not exceptional.

## Decision

Use Kafka as the event backbone, fed exclusively through the [Transactional Outbox](0005-transactional-outbox.md). Topics are per-domain (`payflow.payments`, `payflow.refunds`, `payflow.ledger`), partitioned by aggregate id so per-payment event ordering is preserved within a partition. Consumers (webhook dispatcher, reconciliation sweeper, future analytics) each maintain independent consumer groups and offsets.

## Alternatives Considered

- **Direct synchronous calls / in-process event bus (Spring `ApplicationEventPublisher`) only**: rejected as the sole mechanism — it doesn't survive a consumer being down or a process restart, which is a normal operating condition for webhook delivery specifically. It remains useful for same-transaction in-process reactions, but not for the durable fan-out requirement.
- **A message queue (SQS/RabbitMQ) instead of a log**: rejected. A queue's competing-consumers model is wrong for fan-out to multiple independent consumer types (webhook dispatch and reconciliation both need to see every event, not race for it); Kafka's log model with independent consumer groups fits the requirement directly, and Kafka is explicitly named in the target stack.
- **Polling the outbox table directly from each consumer instead of Kafka**: rejected. Works for one consumer but doesn't scale to N independent consumers without either N polling loops against the same table (contention, duplicated retry logic) or building a pub/sub layer ourselves — which is what Kafka already is.

## Consequences

- Every domain event is durable, replayable, and independently consumable by future modules without touching the producer.
- Requires running and operating a Kafka broker (or managed equivalent) even at small scale — accepted as a fixed cost of the fan-out requirement.
- Ordering is only guaranteed per partition key; consumers are still written to be idempotent as defense in depth (a duplicate or replayed event must be a safe no-op), not because Kafka is expected to duplicate under normal operation.
- The outbox is the only writer to Kafka — no module publishes directly — which keeps "did this get published" a single, auditable question (the outbox row's status) instead of one per module.
