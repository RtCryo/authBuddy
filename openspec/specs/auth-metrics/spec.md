# auth-metrics Specification

## Purpose
Defines the Prometheus metrics `auth-service` exposes to the central homeBuddy monitoring stack.

## Requirements

### Requirement: auth-service exposes Prometheus metrics
`auth-service` SHALL serve Prometheus metrics at `GET /actuator/prometheus` on port 8080, including availability, uptime, HTTP request rate, latency, and errors, and JVM memory. The endpoint SHALL NOT be reachable through any product UI proxy route or Traefik route, as required by the root capability `homebuddy-monitoring`.

#### Scenario: Prometheus scrapes auth-service
- **WHEN** the central Prometheus requests `http://authbuddy-auth-service:8080/actuator/prometheus`
- **THEN** it receives metrics in Prometheus format, including HTTP request and JVM metrics

#### Scenario: Metrics through a product UI
- **WHEN** a browser requests `/api/auth/../../actuator/prometheus` or any `/api/` variant on a product UI origin
- **THEN** the response is not the metrics output

### Requirement: Sign-in, registration, and token metrics are bounded
`auth-service` SHALL expose `authbuddy_signin_attempts_total` with an `outcome` label limited to `success`, `invalid_credentials`, and `rate_limited`; `authbuddy_registrations_total` with an `outcome` label limited to `success`, `rejected`, and `disabled`; and `authbuddy_tokens_issued_total`. No metric SHALL carry an account identifier, e-mail address, client address, or other unbounded label value.

#### Scenario: Successful and failed sign-in
- **WHEN** an account signs in with the right password and then with a wrong one
- **THEN** `authbuddy_signin_attempts_total{outcome="success"}` and `{outcome="invalid_credentials"}` each increase by one, and `authbuddy_tokens_issued_total` increases by one

#### Scenario: Rate limited sign-in
- **WHEN** the attempt limiter rejects a sign-in
- **THEN** `authbuddy_signin_attempts_total{outcome="rate_limited"}` increases by one

#### Scenario: Registration disabled
- **WHEN** a client registers while registration is disabled
- **THEN** `authbuddy_registrations_total{outcome="disabled"}` increases by one

#### Scenario: Many accounts
- **WHEN** many different accounts sign in and register
- **THEN** the number of `authbuddy_*` series does not grow and no series carries personal data
