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

## Endpoints

| Endpoint | Access | Behavior |
|---|---|---|
| `POST /auth/register` | public | Creates an account and returns `201 { token, email }`; `409` for a duplicate email (any case, surrounding whitespace ignored), `400` for invalid input, `403` while `AUTH_REGISTRATION_ENABLED=false`, `429` when rate limited |
| `POST /auth/login` | public | Returns `200 { token, email }`; `401` with the same response for a wrong password and an unknown email, `429` when rate limited |
| `GET /.well-known/jwks.json` | public | Public keys of the current signing key and every previous key, each with its `kid` |
| `GET /actuator/health/**` | public | Health probes |

Everything else is denied. Errors are RFC 9457 problem details that never echo submitted values.

## Accounts

`accounts(id uuid, email, email_normalized unique, password_hash, created_at, updated_at)`. `email_normalized` is the trimmed, lower-cased email; `email` keeps the casing the family typed. Passwords are BCrypt hashes; register accepts 8 to 72 UTF-8 bytes because BCrypt ignores bytes beyond 72. Sign-in verifies against a dummy hash for unknown emails so both failures take the same time.

## Tokens and keys

- Tokens: RS256 JWT with a `kid` header and the claims defined by the root contract; lifetime `JWT_EXPIRATION_MS` (default 24 h).
- Signing key: an RSA key of at least 2048 bits, PKCS#8 PEM, from `AUTH_SIGNING_KEY` (use `\n` for line breaks) or the file named by `AUTH_SIGNING_KEY_FILE`. The service refuses to start without a valid key.
- `kid` is the RFC 7638 SHA-256 thumbprint, so it is stable across restarts.
- Rotation runbook: (1) generate a new key; (2) set it as `AUTH_SIGNING_KEY` and put the old public key into `AUTH_PREVIOUS_PUBLIC_KEYS`; (3) restart; consumers pick up the new `kid` from the JWKS without a restart; (4) after at least one token lifetime, remove the old public key and restart.

## Abuse protection

Sign-in and registration attempts are limited by an in-memory sliding window, separately per client IP and per email (`AUTH_RATE_LIMIT_MAX_ATTEMPTS`, default 10 per minute), and answer `429` with `Retry-After`. The client IP comes from `X-Forwarded-For` only when the request arrives from a private-network proxy (Tomcat's remote-IP handling), so product UI proxies must forward the header (`proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`). The limiter is per instance and resets on restart.

## Legacy import

`java -jar auth-service.jar import-legacy --file=users.csv` imports finanzBuddy users (`email,password_hash`) as accounts with new UUIDs and unchanged BCrypt hashes. The command starts with `auth.mode=import-legacy`: no web server, endpoints, or signing keys exist in that mode, so it cannot be reached over HTTP. Rows are inserted with `ON CONFLICT DO NOTHING`, which makes runs idempotent and keeps emails and hashes out of error messages; only counts are printed. See [`MIGRATION.md`](MIGRATION.md).

## Deployment

`docker-compose.yml` runs PostgreSQL 18 (`authbuddy-postgres`, volume `authbuddy-postgres-data`) and `auth-service` (`authbuddy-auth-service`), both with health checks. `auth-service` joins the external `homebuddy` network with the alias `auth` and the stack's private default network; PostgreSQL is only on the default network.

- Setup: `docker network create homebuddy` (or the root `docker-compose.yml`), copy `.env.example` to `.env` and set `POSTGRES_PASSWORD`, then create the signing key as a file: `mkdir secrets && openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/auth-signing-key.pem`.
- Production: `docker compose -f docker-compose.yml up -d --build`. No host port is published and Traefik does not route to authBuddy; product UIs reach it at `http://auth:8080` through their `/api/auth/` proxies and product APIs read `http://auth:8080/.well-known/jwks.json`.
- Local development: plain `docker compose up -d --build` also loads `docker-compose.override.yml`, which publishes the API on 8090 and PostgreSQL on 5434.
- Secrets: the signing key is mounted as the Docker secret `auth_signing_key` and read through `AUTH_SIGNING_KEY_FILE`, so it never appears in the container environment or an image layer. `.env` and `secrets/` are ignored by Git. The mounted file must be readable by the container user; on a Linux host use `chmod 0644` on the file inside a `0700` directory, or inject the key through `AUTH_SIGNING_KEY` instead.
- Image: multi-stage build (Maven, then a JRE-only Alpine image running as a non-root user).

## Status

Implemented: scaffold, `accounts` migration, register and login, RS256 issuance, the JWKS, the legacy import, the migration runbook, and the Docker stack. Still to come in the change `add-identity-service`: the CI workflow.
