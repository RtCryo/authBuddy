# Compose Deployment Specification

## Purpose

Defines how authBuddy runs and how other homeBuddy services reach it.

## Requirements

### Requirement: authBuddy runs as its own stack on the homeBuddy network
The repository SHALL provide a Docker Compose stack with PostgreSQL and `auth-service`, configured from an `.env` file documented by `.env.example`, with health checks and `authbuddy-` container names. `auth-service` SHALL join the external `homebuddy` network with the alias `auth`, SHALL also join each external identity network defined by the root capability `homebuddy-deployment` with the alias `auth`, and SHALL join the external network `authbuddy-metrics` defined by the root capability `homebuddy-monitoring`. `auth-service` SHALL NOT be published to the host or through Traefik in production. The README or deployment documentation SHALL list the `docker network create --internal` command for every identity network and for `authbuddy-metrics`. Signing keys and database credentials SHALL NOT be committed.

#### Scenario: Reach authBuddy from another service
- **WHEN** a product UI container on the `homebuddy` network calls `http://auth:8080/.well-known/jwks.json`
- **THEN** it receives the published keys

#### Scenario: A product API reaches authBuddy
- **WHEN** a container on `finanzbuddy-identity` or `calendarbuddy-identity` calls `http://auth:8080/.well-known/jwks.json`
- **THEN** it receives the published keys

#### Scenario: Not exposed publicly
- **WHEN** the production stack is running
- **THEN** `auth-service` is reachable only through the product UIs' `/api/auth` proxies and the internal networks

#### Scenario: Missing identity network
- **WHEN** the stack starts on a host where an identity network does not exist
- **THEN** startup fails with a message naming the missing network

#### Scenario: Prometheus scrapes auth-service
- **WHEN** the central Prometheus connects to `authbuddy-auth-service:8080`
- **THEN** the connection succeeds over `authbuddy-metrics`

#### Scenario: Missing metrics network
- **WHEN** the stack starts on a host where `authbuddy-metrics` does not exist
- **THEN** startup fails with a message naming the missing network

### Requirement: Production runs the released auth-service image
The production Compose file SHALL run `auth-service` from `ghcr.io/rtcryo/authbuddy-auth-service:${AUTHBUDDY_VERSION}` without a `build:` section, and SHALL fail with a message naming `AUTHBUDDY_VERSION` when the variable is unset, as the root capability `homebuddy-delivery` requires. `docker-compose.override.yml` SHALL build `auth-service` from `./auth-service` and tag it `authbuddy-auth-service:dev`. `.env.example` SHALL define `AUTHBUDDY_VERSION` with the latest released version. Networks, aliases, and ports are unchanged.

#### Scenario: Deploy a release
- **WHEN** an operator sets `AUTHBUDDY_VERSION=1.0.0` and runs `docker compose -f docker-compose.yml pull` and `up -d`
- **THEN** `authbuddy-auth-service` runs the `1.0.0` image and keeps the aliases and networks it had

#### Scenario: Version variable missing
- **WHEN** `docker compose -f docker-compose.yml up -d` runs without `AUTHBUDDY_VERSION`
- **THEN** Compose fails with a message naming `AUTHBUDDY_VERSION`

#### Scenario: Local development
- **WHEN** a developer runs `docker compose up --build`
- **THEN** `auth-service` is built from the working tree and tagged `authbuddy-auth-service:dev`
