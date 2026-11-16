# Spec Delta

## Purpose

Defines how `auth-service` takes part in homeBuddy tracing. The trace pipeline, naming and propagation convention, retention, and privacy baseline are the root capability `homebuddy-tracing`; this capability adds only authBuddy specifics.

## ADDED Requirements

### Requirement: auth-service exports traces of its requests
`auth-service` SHALL record a server span for every incoming HTTP request other than `/actuator/**` and SHALL export it to the homeBuddy telemetry collector as defined by the root capability `homebuddy-tracing`, with the resource attributes `service.name = authbuddy-auth-service` and `service.namespace = authbuddy`. When a request carries W3C trace context, the span SHALL continue that trace. The export endpoint SHALL be configured by the deployment only, so that `auth-service` exports nothing when it is not set.

#### Scenario: A user signs in
- **WHEN** a user signs in through a product UI
- **THEN** a trace with a `POST /auth/login` server span of `authbuddy-auth-service` is stored in the central trace store

#### Scenario: A product API fetches the JWKS in its request
- **WHEN** a traced product API request fetches `/.well-known/jwks.json` with a `traceparent` header
- **THEN** authBuddy's server span belongs to the product API's trace

#### Scenario: Health checks and scrapes
- **WHEN** the health check or the central Prometheus calls `/actuator/**`
- **THEN** no span of that request is exported

#### Scenario: Collector unavailable
- **WHEN** the collector cannot be reached
- **THEN** sign-in and registration succeed with unchanged latency, and `auth-service` only logs that spans could not be exported

### Requirement: auth-service samples every request by default
`auth-service` SHALL sample every trace it starts by default, SHALL let operators lower the probability with `AUTH_SERVICE_TRACING_SAMPLING_PROBABILITY` without rebuilding, and SHALL follow the sampling decision of an incoming trace context.

#### Scenario: Lower the sampling probability
- **WHEN** an operator sets `AUTH_SERVICE_TRACING_SAMPLING_PROBABILITY=0` and recreates `auth-service`
- **THEN** sign-ins without incoming trace context produce no traces

### Requirement: auth-service spans hold no credentials or personal data
Spans of `auth-service` SHALL NOT contain passwords, password hashes, tokens, signing keys, `Authorization` or cookie headers, request or response bodies, or e-mail addresses. Span names SHALL use route templates.

#### Scenario: Search traces for credentials
- **WHEN** an operator signs in with a known test password and e-mail address and searches the stored spans for them and for the issued token
- **THEN** none is found

### Requirement: auth-service log lines carry the trace id
Every log line `auth-service` writes while handling a traced request SHALL contain `[<traceId>-<spanId>]` with the 32- and 16-character lowercase hexadecimal ids of the current span, so the central log store links it to the trace.

#### Scenario: Failed sign-in log line
- **WHEN** a sign-in fails and `auth-service` logs it
- **THEN** the log line contains the trace id of that request's trace
