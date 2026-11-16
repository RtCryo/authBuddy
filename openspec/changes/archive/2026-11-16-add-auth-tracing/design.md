# Design

## Context

`auth-service` is Spring Boot 4.1.2 on Java 25 with Actuator and the Prometheus registry. It has no tracing dependency and sets no log pattern. It joins `authbuddy-metrics` (root capability `homebuddy-monitoring`), on which the root collector `homebuddy-alloy` receives OTLP on port 4318 (root capability `homebuddy-tracing`). Product APIs call `/.well-known/jwks.json` over their identity networks; product UIs proxy `/api/auth/` to `/auth/`.

## Decisions

### Spring Boot's OpenTelemetry starter

Add `spring-boot-starter-opentelemetry` (Micrometer Tracing with the OpenTelemetry bridge and the OTLP exporter). Configuration in `application.yml`:

- `management.tracing.sampling.probability: 1.0` (Boot's default is 0.1). The default sampler is parent-based, so a caller's decision wins.
- `management.opentelemetry.resource-attributes`: `service.name: authbuddy-auth-service`, `service.namespace: authbuddy`.
- `management.otlp.metrics.export.enabled: false` and `management.logging.export.otlp.enabled: false`: the starter brings `micrometer-registry-otlp`, which would otherwise push metrics to `localhost:4318` every minute.

`management.opentelemetry.tracing.export.otlp.endpoint` is not set in `application.yml`. Boot creates the OTLP span exporter only when that property is present, so tests and local runs outside Compose export nothing. `docker-compose.yml` sets `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://homebuddy-alloy:4318/v1/traces` and `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=${AUTH_SERVICE_TRACING_SAMPLING_PROBABILITY:-1.0}`.

The exporter is the batch span processor with a bounded queue (2048 spans) and a 10 s export timeout; when the collector is unreachable it drops spans and logs a warning, and request threads never wait on it.

### Actuator requests are not exported

The Compose health check calls `/actuator/health/readiness` every 10 s and Prometheus scrapes `/actuator/prometheus` every 15 s. A `SpanExportingPredicate` bean drops server spans whose `uri` tag starts with `/actuator`; the server observation still runs, so `http_server_requests` metrics are unchanged. Spring Security records its own observations (filter chain, authorization) as children of the server span; dropping only the parent would export them as orphan traces. An `ObservationPredicate` therefore skips `spring.security.*` observations whose parent chain leads to a server request for `/actuator/**`.

- *Alternative: an `ObservationPredicate` for the whole request.* It would also remove the actuator requests from the HTTP metrics, which existing dashboards include.

### Logs

With Micrometer Tracing on the classpath, Spring Boot's default console pattern includes the correlation id `[<32 hex>-<16 hex>]`, blank outside a trace. authBuddy keeps the default pattern; the rule "never log passwords, hashes, tokens, or e-mail addresses" is unchanged. `auth-service` wrote no log line per request so far; `AuthController` now logs each sign-in decision with its outcome only (`Sign-in succeeded`, `Sign-in rejected: invalid credentials`, `Sign-in rejected: rate limited`), so a sign-in can be found in the logs from its trace and the other way round.

### Privacy

Spring MVC server spans are named after the method and route pattern (`http post /auth/login`) and record method, status, outcome, and request path, but no headers, query parameters, or bodies. authBuddy adds no custom span attributes. The collector's attribute scrubbing is a second line of defense only.

## Risks / Trade-offs

- [The starter adds the OTLP metrics registry next to the Prometheus registry] → Disabled by property and covered by a test that no OTLP meter registry is active.
- [Exception messages as span events] → authBuddy's exceptions carry no credentials; the root verification searches traces for the test password, token, and e-mail address.
