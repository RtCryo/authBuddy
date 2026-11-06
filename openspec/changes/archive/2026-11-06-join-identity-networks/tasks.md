# Tasks

## 1. Compose

- [x] 1.1 Add `finanzbuddy-identity` and `calendarbuddy-identity` to `auth-service` in `docker-compose.yml` with the alias `auth`, declared as external networks, and update the header comment. Verify with `docker compose config` that `auth-service` lists `default`, `homebuddy`, and both identity networks.
- [x] 1.2 Verify reachability: from a throwaway container on each identity network, `curl http://auth:8080/.well-known/jwks.json` succeeds, and `homebuddy` still resolves `auth`.

## 2. Documentation

- [x] 2.1 Update `docs/ARCHITECTURE.md` (network membership) and the setup and prerequisites in `docs/ARCHITECTURE.md` and `docs/MIGRATION.md` with the `docker network create --internal` commands.
- [x] 2.2 Verify that the networks named in `docker-compose.yml` match the root compose header and `docs/ARCHITECTURE.md`.
