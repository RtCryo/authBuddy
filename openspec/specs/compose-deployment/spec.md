# Compose Deployment Specification

## Purpose

Defines how authBuddy runs and how other homeBuddy services reach it.

## Requirements

### Requirement: authBuddy runs as its own stack on the homeBuddy network
The repository SHALL provide a Docker Compose stack with PostgreSQL and `auth-service`, configured from an `.env` file documented by `.env.example`, with health checks and `authbuddy-` container names. `auth-service` SHALL join the external `homebuddy` network with the alias `auth` and SHALL NOT be published to the host or through Traefik in production. Signing keys and database credentials SHALL NOT be committed.

#### Scenario: Reach authBuddy from another service
- **WHEN** finanzBuddy or calendarBuddy containers on the `homebuddy` network call `http://auth:8080/.well-known/jwks.json`
- **THEN** they receive the published keys

#### Scenario: Not exposed publicly
- **WHEN** the production stack is running
- **THEN** `auth-service` is reachable only through the product UIs' `/api/auth` proxies and the internal network
