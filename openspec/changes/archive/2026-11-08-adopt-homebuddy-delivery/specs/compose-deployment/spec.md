# Spec Delta

## ADDED Requirements

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
