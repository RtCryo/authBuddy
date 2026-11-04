# authBuddy architecture

authBuddy is the homeBuddy identity service. System-wide ownership, contracts, and the port registry are documented in the root [`docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md); this file covers only what is internal to authBuddy.

## Components

| Component | Technology | Responsibility |
|---|---|---|
| `auth-service` | Spring Boot 4.1, Java 25, Spring Security, JPA, Liquibase | Accounts, sign-in, token issuance, JWKS, legacy import |
| PostgreSQL | database `authbuddy` | Accounts |

There is no UI. Product UIs reach `auth-service` through their `/api/auth/` proxies over the external `homebuddy` Docker network, where it is reachable as `auth`.

## Contracts

The root capabilities live under [`../../openspec/`](../../openspec/) (active umbrella change `establish-homebuddy-platform` until it is archived and synced to `openspec/specs/`).

- Tokens issued: `homebuddy-identity-contract` (RS256, issuer `homebuddy-auth`, subject = account UUID).
- Network, aliases, and ports: `homebuddy-deployment`. Local host ports: API 8090, PostgreSQL 5434.

## Status

Only the service scaffold exists: the application skeleton, a stateless security baseline that exposes the health probe, an empty Liquibase changelog, and a context-load test against Testcontainers PostgreSQL. Accounts, tokens, the JWKS, the legacy import, and the Docker stack are added by the change `add-identity-service`; update this file as each part lands.
