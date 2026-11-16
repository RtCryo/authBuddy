# Proposal

## Why

The root change [`add-homebuddy-tracing`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-tracing/proposal.md) adds a central Tempo trace store behind the homeBuddy telemetry collector. Sign-in problems in a product UI involve authBuddy's token endpoint and the product API's JWKS lookup, but `auth-service` records no traces, so an operator cannot see how long authBuddy took or which request failed, and log lines cannot be matched to a request.

## What Changes

- `auth-service` records a trace for every incoming HTTP request and exports it with OTLP over HTTP to the collector, following the root capability `homebuddy-tracing`. It does not restate that contract.
- Resource attributes: `service.name = authbuddy-auth-service`, `service.namespace = authbuddy`.
- Traced calls: incoming requests to `/auth/**` and `/.well-known/jwks.json`. When a product API fetches the JWKS inside its own traced request, authBuddy continues that trace from the W3C `traceparent` header. `auth-service` makes no outgoing HTTP calls of its own.
- Every log line written inside a trace keeps Spring Boot's default console pattern and carries `[<traceId>-<spanId>]`; authBuddy sets no own `logging.pattern.console`. Each sign-in writes one log line with its outcome only, so it can be found from its trace.
- Sampling probability 1.0 by default; operators lower it with `AUTH_SERVICE_TRACING_SAMPLING_PROBABILITY` in `.env`. A downstream decision follows the caller's.
- Health-check and scrape requests to `/actuator/**` are not exported as traces, including Spring Security's spans inside them.
- Spans carry no passwords, tokens, e-mail addresses, or request bodies. OTLP metrics and OTLP log export, which the OpenTelemetry starter would enable, stay off: metrics are scraped by Prometheus and logs collected from Docker.
- The export endpoint is set only in `docker-compose.yml`; without it (tests, `spring-boot:run`) nothing is exported. An unreachable collector never affects sign-in.

## Capabilities

### New Capabilities

- `auth-tracing`: Which requests `auth-service` traces, its resource attributes, sampling, privacy of its spans, and the trace id in its logs.

### Modified Capabilities

- None. `auth-service` already joins `authbuddy-metrics`, which is also the path to the collector.

## Non-goals

- JDBC spans, spans for password hashing or token signing, and tracing of the legacy import runner.
- The trace store, collector, and Grafana, which live in the root workspace.

## Impact

- `auth-service/pom.xml` (`spring-boot-starter-opentelemetry`), `application.yml`, a span export filter for `/actuator/**`, tests.
- `docker-compose.yml` (OTLP endpoint and sampling environment), `.env.example`, `docs/ARCHITECTURE.md`.
- No new network, port, or secret.
