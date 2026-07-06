# Changelog

All notable changes to PayFlow are documented in this file. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Engineering Design Document ([`docs/EDD.md`](docs/EDD.md)) covering system architecture, module boundaries, database schema, API contracts, event catalog, C4 diagrams, sequence diagrams, payment state machine, repository structure, coding standards, implementation roadmap, and risk analysis.
- 12 Architecture Decision Records (`docs/adr/0001`–`0012`) covering the modular monolith, Kafka, PostgreSQL, Redis, transactional outbox, provider abstraction, idempotency strategy, double-entry ledger, multi-tenancy, authentication, webhook reconciliation, and observability decisions.
- Project README establishing PayFlow's identity as a Payment Orchestration Platform.

No application code has been written yet — this is the Phase 0 design milestone.

### Added (M0 — Bootstrap & Tooling)
- Maven reactor (`backend/pom.xml`) on Spring Boot 3.3.5 / Java 21 with two modules: `payflow-core` (the modular monolith skeleton) and `mock-provider-service` (standalone provider simulator skeleton).
- `payflow-core`: Spring Boot application class, `dev`/`test`/`prod` configuration profiles, Flyway baseline migration (`V1__init.sql`, enables `pgcrypto`), and a Testcontainers-backed context-load test that validates the app boots and migrations apply against a real PostgreSQL instance.
- `mock-provider-service`: Spring Boot application class and configuration, with a context-load test.
- `infra/docker-compose.yml`: local development stack (PostgreSQL 16, Redis 7, Kafka in KRaft mode) with health checks and named volumes.
- `.github/workflows/ci.yml`: GitHub Actions CI skeleton building and testing the backend reactor on JDK 21.
- Root `.gitignore` for Maven build output, IDE files, and future frontend artifacts.
- Verified manually end-to-end: `docker compose up` brings all three infra services to healthy; both Spring Boot jars start against that infra, Flyway applies migration V1 against real Postgres, and both `/actuator/health` endpoints report `UP`.

### Added (M1 — Tenancy & Security Core)
- `common` module: `PayFlowException` hierarchy (`ResourceNotFoundException`, `ConflictException`, `DomainValidationException`) with a Stripe-style JSON error envelope; `TenantContext`/`TenantContextHolder` (reads the current request's tenant off Spring Security's `Authentication.getDetails()`); AES-GCM `SymmetricEncryptor` for at-rest secrets.
- `infrastructure` module: `CorrelationIdFilter` (ahead of the security filter chain, so even 401/403 responses carry a trace id) and `GlobalExceptionHandler`.
- Flyway `V2__tenancy_and_security.sql`: `organizations`, `users`, `organization_members`, `api_keys`, `merchants`, `provider_accounts` — including a partial unique index enforcing at most one default provider account per merchant.
- `organization` module: signup (`POST /v1/organizations`, creates an org + OWNER in one transaction), API key issuance/list/revocation (`/v1/organizations/{id}/api-keys`) with Stripe-style `pf_live_`/`pf_test_` keys (BCrypt-hashed secret, indexed lookup prefix), and RBAC membership checks (`OrganizationAccessGuard`).
- `merchant` module: merchant CRUD and provider account creation with encrypted credentials, scoped under `/v1/organizations/{id}/merchants`.
- `security` module: stateless JWT access/refresh tokens (JJWT, HS512), `ApiKeyAuthenticationFilter` and `JwtAuthenticationFilter` (disambiguated by the `pf_` prefix), `SecurityConfig` (stateless session, JSON 401/403 bodies), `POST /v1/auth/login` and `/v1/auth/refresh`.
- Unit tests for `JwtService` and `ApiKeyService` (no Docker required) plus a Testcontainers-backed `AuthFlowIntegrationTest` covering signup, login, refresh, cross-tenant/cross-principal rejection, and the provider-account default-flip.
- Fixed a real Hibernate flush-ordering bug found during manual verification: inserting a new default provider account raced the update that cleared the previous default against the `is_default` partial unique index (inserts flush before updates by default) — fixed with an explicit `flush()` between the two writes.
- Fixed a Flyway/Hibernate schema mismatch (`default_currency` declared `CHAR(3)`, but a plain `String` field validates against `VARCHAR`) caught the same way — via a real Postgres, not a mock.
- Corrected the module dependency table in the EDD (§3): `security` depends on `organization` (to validate API keys and authenticate users), not the other way around; `organization` only needs the `spring-security-crypto` library for password hashing, not the `security` module itself.

### Added (M2 — Payment Lifecycle)
- Relocated `ProviderCode` from `merchant.domain` to `common.provider`, since both `merchant` and `payment` need it and neither should reach into the other's domain package.
- `provider` module: `ProviderClient`/`ProviderRegistry` abstraction (ADR-0006), `MockProviderClient` calling the separate Mock Provider service over real HTTP via Spring's `RestClient`.
- Flyway `V3__payments.sql`: `payments` (`provider_account_id`/`provider_code`/`provider_reference` nullable - resolved at authorize time, not create time, per the EDD's own API contract) and `payment_state_transitions`, with check constraints on all amount columns.
- `payment` module: `Payment`/`PaymentStateTransition` entities (metadata stored as native `jsonb` via Hibernate 6's `@JdbcTypeCode(SqlTypes.JSON)`), the `PaymentStateMachine` (pure, Spring-free, directly unit-tested), and `PaymentService` covering create/authorize/capture/get/list. Merchant-facing API at `/v1/payments/**`, API-key authenticated only.
- `mock-provider-service`: in-memory `ChargeStore` and `POST /provider/v1/charges` / `POST /provider/v1/charges/{id}/capture` — deterministic (always succeeds) for M2; configurable latency/failure simulation is M11.
- 27 unit tests (state machine + service-layer with Mockito) plus a Testcontainers integration test covering the create/get/list/validation/tenant-isolation surface over real HTTP and Postgres (authorize/capture need the separate live Mock Provider service, so those are covered by manual verification instead, documented below).
- **Fixed two real bugs found by writing tests, not by inspection**, before either shipped:
  - `authorize()` and `capture()` were calling the provider *before* validating the state transition was legal — meaning calling `authorize()` twice on an already-authorized payment would have created a **second charge at the provider** before finally failing. Fixed by validating the transition first, so an illegal call never reaches the provider at all (verified with a Mockito `never()` assertion).
  - Illegal-transition errors (double-capture, double-authorize) were returning 400 instead of the intended 409: Spring resolves competing `@RestControllerAdvice` beans by checking advice **beans** in order and using the first one with any applicable handler, not the most specific handler across all beans — so the generic `GlobalExceptionHandler`'s `PayFlowException` catch-all was winning over the more specific `PaymentExceptionHandler`. Fixed by making the generic handler explicitly `@Order(Ordered.LOWEST_PRECEDENCE)` and the specific one `HIGHEST_PRECEDENCE`.
- Fixed an EDD inconsistency caught while writing the create-payment DTO: the ER diagram for `payments` had no `description` column, but the API contract's own example request body included one. Added the column since the migration hadn't been applied anywhere yet.
- Manually verified end-to-end against both live services (16 scenarios): signup → merchant → provider account → API key → create → authorize → capture → double-authorize (409) → double-capture (409) → over-capture (400) → get detail with full transition history → list filtered by merchant/status → cross-org merchant rejection (404) → JWT session rejected from merchant-facing endpoints (403).
