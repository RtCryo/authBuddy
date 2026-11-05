# authBuddy instructions

These instructions apply to work inside `authBuddy/`, in addition to the root [`AGENTS.md`](../AGENTS.md) and [`CLAUDE.md`](../CLAUDE.md) of the homeBuddy workspace.

## Language

All commit messages, pull request texts, code comments, specifications, and documentation are written in English.

## Scope

authBuddy is the homeBuddy identity service. It owns family accounts, issues RS256 JWTs, and publishes a JWKS. It has no UI. Token format and network conventions are defined by the root capabilities `homebuddy-identity-contract` and `homebuddy-deployment`; do not restate them here, reference them.

## Specifications

- Service behavior is specified in `openspec/` in this repository. Contracts shared with other services live in the root `openspec/`.
- Validate with `openspec validate <change> --strict` from this folder.

## Git workflow

- Implement every change on its own branch named after the change, created from an up-to-date `main`. Never commit feature work to `main`.
- Open the pull request only after the change is archived.

## Build and test

Run from `auth-service/`:

```bash
./mvnw verify          # compile, format check, tests (Docker is required for Testcontainers)
./mvnw spotless:apply  # format with google-java-format (AOSP)
```

## Rules

- Never commit secrets, private keys, `.env`, password hashes, or real email addresses; tests use generated keys and fake data.
- Never log passwords, hashes, tokens, or email addresses.
- Database changes go through Liquibase changesets with a rollback; Hibernate runs with `ddl-auto: validate`.
- Keep `docs/ARCHITECTURE.md` aligned with the code when ownership, endpoints, or configuration change.
