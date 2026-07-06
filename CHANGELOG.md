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
