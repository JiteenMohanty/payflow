# PayFlow

**PayFlow is a Payment Orchestration Platform.** It is not a payment gateway, and it does not process card data.

PayFlow sits between merchants and payment providers (Stripe, Razorpay, Adyen, PayPal — currently backed by a Mock Provider for development and testing) and owns the parts of a payment's life that shouldn't have to be rebuilt per provider: a stable merchant-facing API, the payment lifecycle state machine, idempotent request handling, an immutable double-entry ledger, and reliable webhook delivery in both directions.

> **Status: M13 — Deployment complete.** PayFlow now ships as three Docker images (`payflow-core`, `mock-provider-service`, and an Nginx image serving the admin dashboard + reverse-proxying the API), a production Compose file where only Nginx is exposed to the host, and a GitHub Actions pipeline that publishes versioned images to GHCR on every merge to `main`. Verified end-to-end locally with a self-signed certificate standing in for a real one: a full signup-through-capture flow works entirely through Nginx's TLS termination, with both application services fully containerized and talking to each other over the internal network. See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for how to run it on a real VPS, [ADR-0013](docs/adr/0013-deployment-architecture.md) for the reasoning, and [§11 Implementation Roadmap](docs/EDD.md#11-implementation-roadmap) for what ships when.

## Why a platform, not a gateway

A payment gateway processes a charge. An orchestration platform sits above one or more gateways and owns the things merchants otherwise have to build themselves every time they add a second provider: one consistent API and webhook contract, idempotent retries that are actually safe, a ledger that doesn't drift, and reconciliation when a provider's synchronous response and its asynchronous webhook disagree. See [ADR-0011](docs/adr/0011-webhook-reconciliation.md) for why that reconciliation model is the core of what PayFlow does, not a side feature.

## Architecture at a glance

- **Modular Monolith** (Java 21 / Spring Boot 3) — see [ADR-0001](docs/adr/0001-modular-monolith.md) for why this beats microservices at PayFlow's current scale.
- **PostgreSQL** system of record, **Redis** for idempotency caching and rate limiting, **Kafka** as the event backbone fed by a **Transactional Outbox**.
- A separate **Mock Provider service** that behaves like a real external provider — its own latency, failures, retries, and signed async webhooks — so the provider abstraction ([ADR-0006](docs/adr/0006-provider-abstraction.md)) is exercised honestly, not against a convenient fake.
- Full observability by default: Micrometer, Prometheus, Grafana, OpenTelemetry, Jaeger, structured JSON logs with correlation IDs.
- A React 18 / TypeScript admin dashboard for organizations to view payments, ledger entries, webhook history, and issue refunds.

Full architecture (C4 diagrams, database schema, API contracts, event flows, sequence diagrams, and all 12 ADRs) lives in [`docs/EDD.md`](docs/EDD.md) and [`docs/adr/`](docs/adr/README.md).

## Repository Structure

```
payflow/
├── backend/            # Maven reactor: payflow-core (the monolith) + mock-provider-service
├── frontend/            # React admin dashboard
├── infra/               # Docker Compose, Nginx, Prometheus, Grafana configs
├── docs/                # EDD, ADRs, diagrams
├── CHANGELOG.md
└── README.md
```

## Quick Start

```bash
# 1. Start local infra: Postgres, Redis, Kafka
docker compose -f infra/docker-compose.yml up -d

# 2. Build both backend services
cd backend && mvn -DskipTests package

# 3. Run PayFlow Core (applies Flyway migrations against Postgres on startup)
java -jar payflow-core/target/payflow-core.jar
curl http://localhost:8080/actuator/health   # {"status":"UP"}

# 4. In another terminal, run the Mock Provider service
java -jar mock-provider-service/target/mock-provider-service.jar
curl http://localhost:8081/actuator/health   # {"status":"UP"}
```

Try the tenancy flow end-to-end:

```bash
# Sign up an organization + owner
curl -X POST http://localhost:8080/v1/organizations \
  -H "Content-Type: application/json" \
  -d '{"organizationName":"Acme Corp","ownerEmail":"owner@acme.test","ownerFullName":"Ada Owner","password":"correct-horse-battery-staple"}'

# Log in to get a dashboard JWT
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@acme.test","password":"correct-horse-battery-staple"}'

# Use the accessToken from the response to create an API key, a merchant, and a
# default (MOCK) provider account for it - see docs/EDD.md section 5.1 for the
# full request shapes.
curl -X POST http://localhost:8080/v1/organizations/<organizationId>/api-keys \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"environment":"TEST"}'
```

Then run a payment through its lifecycle with the resulting API key:

```bash
# Create a payment intent (no provider call yet)
curl -X POST http://localhost:8080/v1/payments \
  -H "Authorization: Bearer <apiKey>" -H "Content-Type: application/json" \
  -d '{"merchantId":"<merchantId>","amount":"149.00","currency":"USD","description":"Order #10432"}'

# Authorize against the Mock Provider
curl -X POST http://localhost:8080/v1/payments/<paymentId>/authorize \
  -H "Authorization: Bearer <apiKey>" -H "Content-Type: application/json" -d '{}'

# Capture (full or partial)
curl -X POST http://localhost:8080/v1/payments/<paymentId>/capture \
  -H "Authorization: Bearer <apiKey>" -H "Content-Type: application/json" -d '{}'

# See the full state timeline
curl http://localhost:8080/v1/payments/<paymentId> -H "Authorization: Bearer <apiKey>"
```

Retry a mutating call safely with an `Idempotency-Key` - send the same key and body twice, get the same payment back both times instead of creating a duplicate:

```bash
curl -X POST http://localhost:8080/v1/payments \
  -H "Authorization: Bearer <apiKey>" -H "Content-Type: application/json" -H "Idempotency-Key: order-10432-attempt-1" \
  -d '{"merchantId":"<merchantId>","amount":"149.00","currency":"USD","description":"Order #10432"}'
# Run the exact same curl command again - identical response, no second payment created.
```

See the double-entry ledger a capture posted (immutable — `UPDATE`/`DELETE` on these rows is rejected by the database itself, not just the application):

```bash
curl http://localhost:8080/v1/ledger/entries?paymentId=<paymentId> -H "Authorization: Bearer <apiKey>"
```

Refund it (full or partial - omit `amount` to refund whatever's still capturable):

```bash
curl -X POST http://localhost:8080/v1/payments/<paymentId>/refunds \
  -H "Authorization: Bearer <apiKey>" -H "Content-Type: application/json" \
  -d '{"amount":"50.00","reason":"requested_by_customer"}'

# The same ledger endpoint now also shows the reversing entries this refund posted.
curl http://localhost:8080/v1/ledger/entries?paymentId=<paymentId> -H "Authorization: Bearer <apiKey>"
```

Every mutation above also wrote an outbox row, relayed to Kafka by a scheduled poller. Watch it happen directly:

```bash
docker exec payflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payflow.payments --from-beginning --property print.key=true
```

The Mock Provider fires a signed webhook back to PayFlow after every charge operation - watch the reconciliation happen live by capturing directly at the Mock Provider instead of through PayFlow's own `/capture` endpoint (simulating a lost synchronous response):

```bash
curl -X POST http://localhost:8081/provider/v1/charges/<chargeId>/capture \
  -H "Content-Type: application/json" -d '{"amount":"149.00","currency":"USD"}'

# A moment later, PayFlow's own view of the payment has already moved to
# CAPTURED - reconciled purely from the async webhook, actor=PROVIDER_WEBHOOK.
curl http://localhost:8080/v1/payments/<paymentId> -H "Authorization: Bearer <apiKey>"
```

Register your own endpoint to receive payment/refund events - PayFlow signs every delivery (`X-PayFlow-Signature: t=<ts>,v1=<hex hmac-sha256>`) and refuses to register a loopback/private/link-local URL:

```bash
curl -X POST http://localhost:8080/v1/webhook-endpoints \
  -H "Authorization: Bearer <apiKey>" -H "Content-Type: application/json" \
  -d '{"url":"https://your-server.example.com/hooks/payflow","subscribedEvents":["payment.captured","refund.succeeded"]}'
# Response includes a one-time-shown secret - save it, it's never returned again.

# Delivery history for an endpoint:
curl http://localhost:8080/v1/webhook-endpoints/<endpointId>/deliveries -H "Authorization: Bearer <apiKey>"
```

Running the full test suite (`mvn verify`) requires a working Docker environment reachable by Testcontainers. On Windows with Docker Desktop, `docker compose` and plain `docker` commands work fine, but some Docker Desktop builds have a known Testcontainers/docker-java incompatibility over the Windows named pipe API — if `mvn verify` fails with `Could not find a valid Docker environment` while `docker ps` works, this is a local environment issue, not a code issue (GitHub Actions CI runs native Linux Docker, unaffected).

### Frontend: Admin Dashboard

The dashboard is a separate Vite dev server; it proxies `/v1` to `payflow-core` on `:8080`, so no CORS setup is needed.

```bash
cd frontend/admin-dashboard
npm install
npm run dev
# open http://localhost:5174 (or whatever port Vite prints)
```

Log in with the owner account you signed up above. The dashboard resolves `GET /v1/organizations/mine` after login — if the account belongs to exactly one organization it's routed straight into that org's dashboard, otherwise it shows a picker.

The **Metrics** tab embeds the M10 Grafana dashboard directly (`http://localhost:3000` by default — override with `VITE_GRAFANA_URL` in `frontend/admin-dashboard/.env.development`); it needs `infra/docker-compose.yml`'s Grafana service running with `GF_SECURITY_ALLOW_EMBEDDING: "true"` (already set) — Grafana blocks `<iframe>` embedding by default regardless of anonymous-viewer access.

## Deployment

PayFlow ships as three Docker images (`payflow-core`, `mock-provider-service`, and an Nginx image serving the built admin dashboard + reverse-proxying the API), built and published to GHCR by GitHub Actions on every merge to `main`. `infra/docker-compose.prod.yml` runs the whole stack on a single VPS with only Nginx exposed to the host. See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for the full walkthrough (TLS via Let's Encrypt, Grafana access, running the stack) and [ADR-0013](docs/adr/0013-deployment-architecture.md) for why it's shaped this way.

## Roadmap

See [§11 Implementation Roadmap](docs/EDD.md#11-implementation-roadmap) in the EDD for the full milestone sequence (M0 bootstrap through M14 release polish), and [§14 Future Extensibility](docs/EDD.md#14-future-extensibility) for what's designed-in but deliberately out of scope for v1 (real provider integrations, disputes, subscriptions, multi-currency settlement, platform fees).

## Documentation

- [Engineering Design Document](docs/EDD.md) — architecture, schema, API contracts, event flows, diagrams, roadmap, risk analysis
- [Architecture Decision Records](docs/adr/README.md)
- [Deployment Guide](docs/DEPLOYMENT.md) — running the production stack on a real VPS
- [Changelog](CHANGELOG.md)
