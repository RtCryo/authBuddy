# Tasks

## 1. Metrics

- [x] 1.1 Add `micrometer-registry-prometheus`, expose `health,prometheus` in `application.yml`, and permit `GET /actuator/prometheus` in `SecurityConfig`. Verify with a test that the endpoint returns Prometheus text without authentication and that other actuator paths stay denied.
- [x] 1.2 Record `authbuddy_signin_attempts_total{outcome}`, `authbuddy_registrations_total{outcome}`, and `authbuddy_tokens_issued_total` at the decision points named in design.md, with fixed outcome sets. Verify with tests for every outcome, and that no label holds an e-mail address, account id, or address.

## 2. Compose and documentation

- [x] 2.1 Join `auth-service` to the external `authbuddy-metrics` network in `docker-compose.yml` and update the header comment. Verify with `docker compose config` and that startup without the network fails naming it.
- [x] 2.2 Update `docs/ARCHITECTURE.md` and `docs/MIGRATION.md` with the metrics endpoint, metric names, and the `docker network create --internal authbuddy-metrics` command.

## 3. Verification

- [x] 3.1 With the root stack running, verify that `authbuddy-auth-service` is up in the central Prometheus, that a sign-in and a failed sign-in change the sign-in counter, and that the authBuddy dashboard shows them.
- [x] 3.2 Verify that `/actuator/prometheus` is not served through `localhost:3000` or `localhost:3010`, including `/api/auth/../../actuator/prometheus`, and that no metric label holds personal data after several accounts signed in.
