# ADR-0004: Redis for Idempotency Cache & Rate Limiting

**Status:** Accepted
**Date:** 2026-07-06

## Context

Two cross-cutting needs are latency-sensitive and don't belong in the primary transactional path as the only mechanism: (1) replaying an idempotent response fast for the common case of a client retrying a request it already completed, and (2) per-API-key rate limiting (Bucket4j) that needs a shared counter across however many PayFlow core instances are running, checked on every request.

## Decision

Use Redis as a cache/coordination layer in front of Postgres, never as the source of truth. Idempotency: Postgres holds the durable `idempotency_keys` row (source of truth, checked with `FOR UPDATE` on cache miss); Redis holds a short-TTL cache of `(org, key) -> response` for the fast path, and a short-lived lock key to reduce (not replace) DB contention on concurrent duplicate requests. Rate limiting: Bucket4j backed by Redis so limits are enforced consistently across all PayFlow core instances.

## Alternatives Considered

- **Redis as the sole idempotency store (no DB persistence)**: rejected — see [ADR-0007](0007-idempotency-strategy.md). A cache eviction or Redis restart must never cause a duplicate charge; only a durable store can be the source of truth for that guarantee.
- **In-memory (per-instance) rate limiting**: rejected. With multiple PayFlow core instances behind a load balancer, per-instance counters let a client exceed the intended limit by a multiple of the instance count; a shared Redis-backed bucket is required for the limit to mean what it says.
- **Postgres-only rate limiting (no Redis)**: rejected on latency grounds — rate limit checks happen on every request and a Redis `INCR`/Lua script round-trip is materially cheaper than a DB round-trip on the hot path.

## Consequences

- Redis unavailability degrades but does not break correctness: idempotency falls back to the DB path (slower, still correct); rate limiting fails open to a conservative default rather than rejecting all traffic (see Risk Analysis in the EDD).
- Two idempotency read paths (Redis fast path, DB authoritative path) must be kept behaviorally identical — covered by integration tests that exercise both a cache hit and a cold-cache DB lookup for the same scenario.
- Adds an operational dependency (Redis) that must be monitored and sized, but scoped narrowly to cache/coordination duties, not data of record.
