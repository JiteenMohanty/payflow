# Changelog

All notable changes to PayFlow are documented in this file. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Engineering Design Document ([`docs/EDD.md`](docs/EDD.md)) covering system architecture, module boundaries, database schema, API contracts, event catalog, C4 diagrams, sequence diagrams, payment state machine, repository structure, coding standards, implementation roadmap, and risk analysis.
- 12 Architecture Decision Records (`docs/adr/0001`–`0012`) covering the modular monolith, Kafka, PostgreSQL, Redis, transactional outbox, provider abstraction, idempotency strategy, double-entry ledger, multi-tenancy, authentication, webhook reconciliation, and observability decisions.
- Project README establishing PayFlow's identity as a Payment Orchestration Platform.

No application code has been written yet — this is the Phase 0 design milestone.
