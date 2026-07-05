# ADR-0011: Webhooks as Reconciliation Source of Truth

**Status:** Accepted
**Date:** 2026-07-06

## Context

A synchronous call to a payment provider can fail to deliver its response even when the underlying operation succeeded — the client can time out, a load balancer can drop the connection, a network partition can occur after the provider commits the charge server-side. If PayFlow trusts only the synchronous response, these situations leave a payment stuck `AUTHORIZED` when the provider actually captured it, silently wrong until someone notices. Real providers (and the Mock Provider, per the product requirements) send asynchronous webhooks specifically to cover this gap.

## Decision

Treat the provider's synchronous REST response as **optimistic, immediate feedback** to return to the merchant quickly, but treat the provider's asynchronous webhook as the **reconciliation source of truth**. Concretely: `InboundWebhookProcessor` verifies the HMAC signature, deduplicates on `(provider_code, provider_event_id)`, loads the payment by `provider_reference`, and applies the event as a state confirmation — if the payment is still in the state the webhook is confirming a transition *from*, apply the transition (with its own ledger posting and outbox event, identical to the synchronous path); if the payment already reflects the transition (because the synchronous call actually did succeed), the webhook is a no-op confirmation, not a duplicate charge or duplicate ledger posting. A scheduled `ReconciliationSweeper` additionally cross-checks payments stuck in a non-terminal state past an expected window against the provider directly, as a backstop for a lost webhook.

## Alternatives Considered

- **Trust only the synchronous response, ignore/log webhooks**: rejected — this is precisely the gap described above; it would make the webhook infrastructure decorative rather than load-bearing, contradicting the product requirement that webhook processing be a real part of the lifecycle.
- **Trust only webhooks, make the synchronous API call fire-and-forget**: rejected — merchants need an immediate response to their synchronous call for their own UX (e.g. showing an order confirmation), and the product requirement explicitly includes synchronous lifecycle endpoints, not a purely async model.
- **Last-write-wins on whichever arrives (sync response or webhook) without idempotent transition checks**: rejected — this reintroduces exactly the double-processing risk the state machine's transition validation exists to prevent; both paths must go through the same `PaymentStateMachine.transition(...)` guard.

## Consequences

- The synchronous provider-call code path and the inbound-webhook code path must both funnel through the same state transition and ledger-posting logic — no duplicated business logic between "capture confirmed synchronously" and "capture confirmed via webhook."
- Webhook signature verification and event deduplication are non-negotiable, always-on steps, not a configurable option — an unverified or duplicate webhook must never be able to move payment state.
- This is why webhook processing is on the critical path of "is this payment actually correctly captured," not a side channel for notifications only — it directly informs the Observability requirement to track "payments pending reconciliation" as a business metric.
- The `ReconciliationSweeper` (M9) is the backstop for the case where the webhook itself is lost — this decision is incomplete without it, since a webhook-as-truth model that has no fallback for a lost webhook has the same blind spot as trusting only the sync response.
