# ADR-0006: Provider Abstraction via Adapter Interface

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow's entire value proposition is presenting one stable API to merchants regardless of which provider actually processes a transaction, and adding Stripe/Razorpay/Adyen/PayPal later must not require touching `payment`, `refund`, or `ledger`. The Mock Provider exists today; real providers don't exist yet but the seam for them must already be correct, because retrofitting a provider abstraction under existing business logic is exactly the kind of rewrite this architecture is meant to avoid.

## Decision

Define a single `ProviderClient` interface in the `provider` module (`authorize`, `capture`, `refund`, plus signature verification for that provider's webhook format) returning provider-agnostic result types (`ProviderAuthorizationResult`, etc.) that already model the states PayFlow's state machine needs (`AUTHORIZED`, `DECLINED`, `PENDING`). A `ProviderRegistry` resolves the correct adapter by `provider_code` read from the merchant's `provider_accounts` row. `payment` and `refund` depend only on `ProviderClient`, never on a concrete adapter. The Mock Provider is the first (and for v1, only) adapter implementation, talking to the separate Mock Provider service over HTTP exactly as a real provider integration would.

## Alternatives Considered

- **Provider-specific code paths inside `payment`/`refund` (`if providerCode == "stripe" ...`)**: rejected outright — this is precisely the coupling the abstraction exists to prevent, and it's how a "just get it working" mock integration quietly becomes unremovable technical debt.
- **Building the Mock Provider as in-process fake classes instead of a real HTTP service**: rejected. An in-process fake can't exercise the failure modes that make the abstraction meaningful — network latency, timeouts, signature verification, async webhook delivery. A real HTTP service (per the product requirements) forces the adapter and the reconciliation logic ([ADR-0011](0011-webhook-reconciliation.md)) to be genuinely correct, not correct-against-a-fake.
- **Webhook payload normalization inside each provider's raw format passed through to `payment`**: rejected — `payment` would then need to understand every provider's event vocabulary. Each adapter normalizes its provider's webhook payload into PayFlow's internal event shape before it reaches domain logic.

## Consequences

- Adding Stripe means: implement `StripeProviderClient`, register it, add its credentials shape to `provider_accounts` — no change to payment lifecycle, ledger, or state machine code.
- The interface must be designed generously enough for providers with different capabilities (e.g. some providers don't support partial capture) — represented as capability flags on the adapter rather than assuming every provider does everything the Mock Provider does.
- Testing `payment`/`refund` business logic uses a test double implementing `ProviderClient` directly (fast, in-process, deterministic), while integration tests exercise the real Mock Provider service over HTTP (slow, realistic, catches wiring bugs) — both are required, neither substitutes for the other.
