# Tasks

## 1. Scaffolding

- [x] 1.1 Initialize `authBuddy/` as its own Git repository with `auth-service/` (Spring Boot 4.1, Java 25, web, security, validation, JPA, Liquibase, PostgreSQL, actuator), `docs/`, `AGENTS.md`, `.env.example`; verify `./mvnw verify` passes.

## 2. Accounts and tokens

- [x] 2.1 Add the `accounts` changeset with normalized unique email and rollback; verify migration test.
- [x] 2.2 Implement register/login with finanzBuddy-compatible validation and responses, BCrypt, registration switch, and rate limiting; verify integration tests for success, duplicate (any case), invalid input, wrong credentials, disabled registration, and 429.
- [x] 2.3 Implement RS256 issuance with `kid`, issuer, audiences, and expiry, and the JWKS endpoint with current and previous keys; verify tests decode tokens with only the JWKS and cover rotation and startup without a key.

## 3. Legacy import

- [x] 3.1 Implement the idempotent `import-legacy` command with count-only reporting; verify tests for new, existing, case-variant, and malformed rows, and that imported users sign in with their old passwords.
- [x] 3.2 Document the export query for finanzBuddy and the migration runbook in `docs/`.

## 4. Deployment and verification

- [ ] 4.1 Add Dockerfile, compose stack on the `homebuddy` network with alias `auth`, health checks, and secret handling; verify the stack starts and a product UI proxy reaches it.
- [ ] 4.2 Add CI workflow; run tests and `openspec validate add-identity-service --strict`.
