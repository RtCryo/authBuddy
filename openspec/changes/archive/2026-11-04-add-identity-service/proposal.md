# Proposal

## Why

A family should use one homeBuddy account for every homeBuddy service. Today finanzBuddy's API Gateway owns users and signs HMAC tokens that only it can verify, and calendarBuddy is about to start. A dedicated identity service lets both products accept the same account without sharing signing secrets or each other's databases.

## What Changes

- Create `authBuddy` as its own Git repository with an `auth-service` (Spring Boot 4.1, Java 25, Liquibase, PostgreSQL) and Docker Compose deployment.
- Own homeBuddy accounts (UUID id, email unique ignoring case, BCrypt hash) with `POST /auth/register` and `POST /auth/login`, keeping finanzBuddy's request/response contract (`{ token, email }`) and validation rules.
- Issue RS256 JWTs (`iss=homebuddy-auth`, `sub` = account UUID, `email`, `aud=[finanzbuddy, calendarbuddy]`, configurable expiry) and publish signing keys at `GET /.well-known/jwks.json` with key-id based rotation.
- Provide a one-time import of existing finanzBuddy users (email and existing BCrypt hash), so current users keep their passwords.
- Provide the external `homebuddy` Docker network on which services reach authBuddy as `auth`.

## Capabilities

### New Capabilities

- `account-identity`: Account registration, sign-in, token issuance, JWKS publication, and key rotation.
- `account-import`: One-time, idempotent import of legacy finanzBuddy credentials and the registration freeze during migration.
- `compose-deployment`: authBuddy stack, `homebuddy` network, and secret handling.

### Modified Capabilities

- None (new repository).

## Non-goals

- A login UI, single sign-on sessions across product domains, refresh tokens, OAuth2/OIDC provider features, social login, password reset, e-mail verification, or multi-factor authentication. Password reset is the most likely follow-up.
- Per-member identities inside a family account.

## Dependencies

- Coordinated by the root umbrella change [`establish-homebuddy-platform`](../../../../../openspec/changes/archive/2026-11-05-establish-homebuddy-platform/proposal.md) (rollout step 1). Token format and validation rules are defined once by the root capability `homebuddy-identity-contract`; network, aliases, and ports by `homebuddy-deployment`. authBuddy is the issuer side of that contract.

## Impact

- New repository `homeBuddy/authBuddy/` and database `authbuddy`.
- finanzBuddy ([`adopt-homebuddy-identity`](../../../../../finanzBuddy/openspec/changes/archive/2026-11-05-adopt-homebuddy-identity/proposal.md)) and calendarBuddy ([`add-calendar-buddy`](../../../../../calendarBuddy/openspec/changes/archive/2026-11-05-add-calendar-buddy/proposal.md)) become token consumers via JWKS.
- New secrets: RSA private key(s) for signing; finanzBuddy's `JWT_SECRET` becomes obsolete after its migration.
