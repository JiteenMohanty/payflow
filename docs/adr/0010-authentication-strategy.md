# ADR-0010: API Keys (merchants) + JWT (dashboard)

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow has two distinct classes of caller with different needs: merchant backend systems calling the REST API programmatically (long-lived credentials, no interactive login, need environment separation between test and live), and human org admins using the browser dashboard (interactive login, sessions that expire, need password-based auth and eventually MFA).

## Decision

Two separate authentication mechanisms, matched to caller type:

- **Merchant API**: API keys (`Authorization: Bearer pf_<env>_<secret>`), stored as `key_prefix` (public, shown in the dashboard for identification) + `hashed_secret` (bcrypt, the raw secret is shown exactly once at creation and never retrievable again). Keys carry an `environment` (`LIVE`/`TEST`) so merchants can integrate against a sandbox without touching real ledger/provider state. A `SecurityFilter` resolves the key to a `TenantContext` (organization + rate-limit bucket) before the request reaches any controller.
- **Admin Dashboard**: short-lived JWT access tokens + refresh tokens issued via `POST /v1/auth/login` (email/password, bcrypt-hashed), scoped to the organizations the user is a member of via `organization_members.role` (RBAC: `OWNER`, `ADMIN`, `ANALYST`).

## Alternatives Considered

- **One mechanism for both (e.g. JWT everywhere, including machine-to-machine)**: rejected — API keys are the correct primitive for long-lived machine credentials (no login flow, easy to rotate/revoke, environment-scoped); forcing merchants through an OAuth-style token exchange for simple server-to-server calls adds integration friction with no security benefit here.
- **Storing raw API key secrets (reversibly encrypted) instead of hashing**: rejected — a hash is sufficient since PayFlow never needs to redisplay the secret, and hashing (not just encryption) means a database compromise doesn't expose usable credentials directly.
- **Session cookies for the dashboard instead of JWT**: considered; JWT was chosen for statelessness across PayFlow core instances (no shared session store required) and because the dashboard is a separate SPA origin from the API, where bearer tokens are the more conventional fit than cookie-based sessions.

## Consequences

- Revoking a merchant's access is a `status=REVOKED` update on one `api_keys` row, checked on every request — immediate, no cache invalidation problem.
- The `TEST`/`LIVE` environment split on API keys must be threaded through to the provider layer too (test-environment payments should route to a sandbox-safe path) — a constraint future provider adapters must respect.
- JWT access tokens are short-lived by design; the dashboard must implement silent refresh — a frontend concern documented for the M12 milestone, not deferred silently.
- Rate limiting (Bucket4j, see [ADR-0004](0004-redis.md)) is keyed off the resolved API key/organization, not the raw request, so it's consistent regardless of which key a merchant uses across multiple keys.
