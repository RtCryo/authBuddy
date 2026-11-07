## MODIFIED Requirements

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
