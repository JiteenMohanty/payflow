# ADR-0007: Stripe-style Idempotency Key Design

**Status:** Accepted
**Date:** 2026-07-06

## Context

Merchants integrating with a payment API will retry on timeouts — this is correct client behavior, not a bug, since a timeout doesn't tell the client whether the server-side operation actually completed. Without idempotency, a retried `POST /payments/{id}/capture` after a timeout risks a double capture. This must be safe by construction, not by asking merchants to be careful.

## Decision

Adopt Stripe's `Idempotency-Key` header model. On every mutating endpoint, the client supplies a key; PayFlow stores `(organization_id, key) -> request_fingerprint, response_status_code, response_body` durably in Postgres, with Redis as a fast-path cache (see [ADR-0004](0004-redis.md)). Behavior:

- **New key**: acquire a DB row (`status=IN_PROGRESS`) inside the same flow, execute business logic, persist the response, mark `COMPLETED`, cache in Redis.
- **Same key, same fingerprint, `COMPLETED`**: replay the stored response byte-for-byte. No business logic re-executes.
- **Same key, same fingerprint, `IN_PROGRESS`**: `409 Conflict` — a concurrent duplicate is genuinely in flight; the client should retry shortly, not assume failure.
- **Same key, different fingerprint**: `422` — the key was reused for a different request body, which is a client bug we refuse to silently misinterpret.
- Keys expire (`expires_at`, default 24h) and are purged by a scheduled cleanup job.

## Alternatives Considered

- **Database unique constraint on business fields alone (e.g. one capture per payment) instead of an idempotency key**: rejected as insufficient on its own — it prevents a double *capture* but doesn't generalize to arbitrary mutating endpoints (webhook registration, refunds with client-chosen amounts) the way a generic key does, and it's exactly the mechanism Stripe's API — the model merchants already expect — does not use.
- **Idempotency handled client-side only (merchants deduplicate before calling)**: rejected — pushes a correctness requirement onto every integrator instead of the platform; the entire point of an idempotency key is that the platform, not the caller, guarantees safety.
- **Redis-only idempotency (no DB persistence)**: rejected — see [ADR-0004](0004-redis.md); a cache is not a safe place to be the only record of "did we already charge this."

## Consequences

- Every mutating endpoint's controller signature includes an `Idempotency-Key` requirement, enforced by a shared interceptor rather than per-controller logic — one implementation, not N copies.
- The `request_fingerprint` must be a stable hash of method + path + normalized body; this means DTOs must serialize deterministically (stable field order, no floating timestamps embedded) or the fingerprint will spuriously mismatch identical logical requests.
- Idempotency correctness is testable independently of business logic: the integration test suite includes a generic "replay this request twice" harness applied to every mutating endpoint, not hand-written per endpoint.
