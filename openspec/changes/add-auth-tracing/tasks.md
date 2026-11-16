# Tasks

## 1. Tracing in auth-service

- [ ] 1.1 Add `spring-boot-starter-opentelemetry` and the `application.yml` settings from design.md (sampling 1.0, resource attributes, OTLP metrics and log export off, no export endpoint). Verify with a test that no OTLP meter registry and no span exporter is active without the endpoint property, and that `./mvnw verify` passes.
- [ ] 1.2 Add the `SpanExportingPredicate` that drops server spans of `/actuator/**`. Verify with a unit test for an actuator span and a `/auth/login` span.
- [ ] 1.3 Verify with a test that a log line written inside a traced request contains `[<traceId>-<spanId>]` with the current ids.

## 2. Compose and documentation

- [ ] 2.1 Set `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` and `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=${AUTH_SERVICE_TRACING_SAMPLING_PROBABILITY:-1.0}` on `auth-service` in `docker-compose.yml`, document the variable in `.env.example`, and update the header comment. Verify with `docker compose config`.
- [ ] 2.2 Update `docs/ARCHITECTURE.md` with the tracing settings, the resource attributes, and a link to the root capability `homebuddy-tracing`.

## 3. Verification

- [ ] 3.1 With the root stack running, sign in through a product UI and verify that Tempo has a trace with `service.name = "authbuddy-auth-service"`, that the matching log line in Loki contains its trace id, and that no `/actuator` trace is stored.
- [ ] 3.2 Stop `homebuddy-alloy`, sign in, and verify that sign-in succeeds and `auth-service` only logs export warnings.
