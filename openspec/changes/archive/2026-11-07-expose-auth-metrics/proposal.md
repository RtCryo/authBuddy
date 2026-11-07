# Proposal

## Why

The root change [`add-homebuddy-monitoring`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-monitoring/proposal.md) adds one central Prometheus and Grafana for every homeBuddy service. authBuddy, which every product depends on for sign-in, exposes no metrics today: `auth-service` publishes only `health` and has no Prometheus registry, so its availability, sign-in failures, and rate limiting are invisible.

## What Changes

- `auth-service` exposes `/actuator/prometheus` with basic service metrics (availability, uptime, HTTP rate, latency and errors, JVM) and three business metrics with bounded labels:
  - `authbuddy_signin_attempts_total{outcome}` with `outcome` in `success`, `invalid_credentials`, `rate_limited`;
  - `authbuddy_registrations_total{outcome}` with `outcome` in `success`, `rejected`, `disabled`;
  - `authbuddy_tokens_issued_total`.
- The security rules permit `/actuator/prometheus`. It stays unreachable through the product UI proxies, which forward only `/api/auth/` to `/auth/`.
- `auth-service` joins the external, internal-only network `authbuddy-metrics`, so the central Prometheus can scrape `authbuddy-auth-service:8080` without joining `homebuddy`.
- Documentation lists the `docker network create --internal authbuddy-metrics` command.

The monitoring contract (networks, scrape and label conventions, Grafana portal) is the root capability `homebuddy-monitoring`; this change does not restate it.

## Capabilities

### New Capabilities

- `auth-metrics`: The metrics `auth-service` exposes and how they stay bounded and private.

### Modified Capabilities

- `compose-deployment`: `auth-service` also joins `authbuddy-metrics`.

## Non-goals

- Dashboards, scrape configuration, and Grafana, which live in the root `monitoring/` folder.
- Metrics per account, per e-mail address, or per client address.
- Alerting.

## Impact

- `auth-service/pom.xml` (Micrometer Prometheus registry), `application.yml`, `SecurityConfig`, `AccountService`/`AuthController`/`TokenService` instrumentation, tests.
- `docker-compose.yml`, `docs/ARCHITECTURE.md`, `docs/MIGRATION.md`.
- Hosts must create `authbuddy-metrics` before starting authBuddy.
