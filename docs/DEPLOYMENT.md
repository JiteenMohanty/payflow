# Deployment Guide

This describes deploying PayFlow to a single VPS running Docker Compose, per [ADR-0013](adr/0013-deployment-architecture.md). Read that ADR first for the reasoning behind every decision below.

> **Honesty note:** this project owns no domain and no provisioned VPS. Every step below has been verified as far as it can be without real infrastructure - the Docker images build and the full stack (Postgres, Redis, Kafka, both application services, the observability stack, and Nginx) has been run end to end locally with a self-signed certificate standing in for a real one. The one thing that has *not* been executed for real is Let's Encrypt certificate issuance against an actual owned domain - the certbot configuration is complete and follows the standard, well-documented webroot flow, but is disclosed here as unverified rather than claimed as tested.

## Prerequisites

- A VPS (Hetzner, DigitalOcean, or similar) with Docker Engine and the Docker Compose plugin installed, and ports 80/443 open.
- A domain with an A record pointing at the VPS's IP address.
- `git` on the VPS (to clone this repository) - or just copy the `infra/` directory and the top-level `docker-compose.prod.yml` if you'd rather not check out the whole source tree on a production host at all, since `docker compose pull` never needs the source.

## 1. Get the compose file and config onto the VPS

```bash
git clone https://github.com/JiteenMohanty/payflow.git
cd payflow/infra
```

## 2. Configure secrets

```bash
cp .env.example .env
```

Fill in every value in `.env` - it is gitignored and must never be committed. `.env.example` documents how to generate each secret (`openssl rand ...`). `DOMAIN` must match the domain your DNS A record points at.

## 3. TLS certificates

Nginx's `443` server block requires `./nginx/ssl/fullchain.pem` and `./nginx/ssl/privkey.pem` to exist before it will even start - and the standard Let's Encrypt webroot flow needs Nginx running on port 80 to serve the ACME challenge. Bootstrap that chicken-and-egg with a temporary self-signed certificate first:

```bash
mkdir -p nginx/ssl
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -keyout nginx/ssl/privkey.pem -out nginx/ssl/fullchain.pem \
  -subj "/CN=localhost"
```

Start the stack so Nginx is serving on port 80 (needed for the ACME HTTP-01 challenge):

```bash
docker compose -f docker-compose.prod.yml up -d
```

Request the real certificate (webroot method, matching the `/.well-known/acme-challenge/` location already in `nginx.conf`). Deliberately not `run --rm` here - the container needs to still exist for the `docker cp` step below, so it's removed manually afterward instead:

```bash
docker compose -f docker-compose.prod.yml run --name certbot-issue certbot \
  certonly --webroot -w /var/www/certbot \
  -d "$DOMAIN" --email you@example.com --agree-tos --no-eff-email

docker cp certbot-issue:/etc/letsencrypt/live/$DOMAIN/fullchain.pem nginx/ssl/fullchain.pem
docker cp certbot-issue:/etc/letsencrypt/live/$DOMAIN/privkey.pem nginx/ssl/privkey.pem
docker rm certbot-issue

docker compose -f docker-compose.prod.yml restart nginx
```

**Renewal:** Let's Encrypt certificates expire after 90 days. Re-run the `certbot certonly` command above (it's a no-op renewal if the cert is still valid for a while, and re-issues if not) on a cron job, followed by the same `docker cp` + `nginx restart` steps - or wrap all four commands in a single script invoked from cron. This project does not ship an automated renewal script, since it can't be exercised against a real certificate to prove it works; scripting it is a mechanical extension of the four commands above.

## 4. Grafana Basic Auth

The `/grafana/` path (embedded live in the admin dashboard's Metrics page) is gated by HTTP Basic Auth at the Nginx layer, since Grafana itself only has anonymous *viewer* access enabled (see [ADR-0013](adr/0013-deployment-architecture.md)):

```bash
docker run --rm httpd:alpine htpasswd -Bbn <username> <password> > nginx/.htpasswd
```

Anyone who knows this username/password can view (but not edit) every Grafana dashboard. Editing Grafana itself (data sources, alert rules) uses Grafana's own admin login, set via `GRAFANA_ADMIN_PASSWORD` in `.env`.

## 5. Run the stack

Real deployment - pulls the images CI already built and published to GHCR (see [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)'s `publish-images` job), no local build, no source checkout needed for this step:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Local verification / building from source instead of pulling (what this project's own manual verification pass used, since there is no real GHCR image to pull from yet without pushing first):

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Check everything is healthy:

```bash
docker compose -f docker-compose.prod.yml ps
curl -k https://localhost/actuator/health   # or https://$DOMAIN/actuator/health from outside
```

## 6. Deploying a new version

Once CI has published new images for a merge to `main`:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Compose recreates only the containers whose image actually changed. To roll back, pull a specific commit-SHA tag instead of `:latest` (every image is published as both `:latest` and `:<git-sha>` - see the `publish-images` job) and re-run `up -d` with that tag substituted in `docker-compose.prod.yml`.

## What's exposed vs. internal-only

Only `nginx` publishes ports to the host (80/443). Everything else - Postgres, Redis, Kafka, Prometheus, Jaeger, the OTel Collector, Grafana, and both application services - is reachable only on the internal Compose network. To inspect any of them directly (e.g. `psql` into Postgres, or Grafana's own admin UI without going through `/grafana/`), use `docker compose exec <service> ...` or an SSH tunnel - never open their ports on the VPS's firewall.

## Mock Provider in production

`docker-compose.prod.yml` deploys `mock-provider-service` in production the same as every other environment - this project has no real provider integration to deploy instead of it (see [ADR-0013](adr/0013-deployment-architecture.md#consequences)). A real deployment integrating an actual provider (Stripe, Razorpay, etc. - see the EDD's Future Extensibility section) would add that provider's adapter and credentials alongside Mock Provider, not replace this deployment model.
