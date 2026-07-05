# ADR-0009: Multi-Tenancy via Row-Level Organization Scoping

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow serves multiple organizations from one deployment and one database. A cross-tenant data leak — one organization seeing another's payments, ledger, or webhook data — is not a minor bug for a fintech product; it's the kind of failure that ends the product. The isolation mechanism needs to be something a reviewer can verify by reading a query, not something that depends on every developer remembering a join correctly.

## Decision

Shared schema, shared tables, tenant isolation enforced at the row level: every tenant-scoped table carries `organization_id` **directly** (denormalized onto `payments` and `refunds` even though it's reachable via `merchants`, not just on tables one join away). Every repository method that reads or writes these tables takes `organizationId` as an explicit parameter and includes it in the `WHERE` clause — there is no repository method that fetches a payment by id alone. This is checked structurally: integration tests assert that fetching another organization's resource by id returns empty/`404`, never the resource.

## Alternatives Considered

- **Schema-per-tenant or database-per-tenant**: rejected for v1 — operationally heavy (migrations run N times, connection pool sizing multiplies), and unnecessary at the scale PayFlow is designed for now; revisit only if a specific compliance requirement (a merchant demanding physical data isolation) forces it, which would warrant its own ADR.
- **Row-Level Security (Postgres RLS) via session variables**: considered as a defense-in-depth layer. Not adopted as the *primary* mechanism for v1 because it adds a class of bugs around session variable propagation through connection pools that's easy to get subtly wrong; the explicit-parameter approach is simpler to reason about and to test. Revisiting RLS as an additional enforcement layer is a reasonable future hardening step, not a contradiction of this decision.
- **Relying on joins through `merchants`/`organizations` without denormalizing `organization_id`**: rejected — every query would need an extra join to enforce isolation, which is easy to forget on a new query; denormalizing makes the tenant filter a single indexed column check that's hard to omit accidentally (and caught by ArchUnit/tests when it is).

## Consequences

- Every new tenant-scoped table added in future milestones must carry `organization_id` from its first migration — this is now a checklist item for schema review, not optional.
- Every new repository method on a tenant-scoped entity must accept `organizationId` — enforced by code review and by the cross-tenant integration test suite, which runs against every new endpoint.
- Denormalization means `organization_id` must be kept consistent if a payment's merchant were ever reassigned to a different organization — not a supported operation in v1; if it becomes one, this ADR is revisited.
