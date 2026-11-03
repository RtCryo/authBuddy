# Design

## Context

See [proposal](proposal.md). finanzBuddy's gateway stores `users(id bigint, email, password_hash)` and issues HS256 JWTs with the email as subject; several gateway tables reference `users(id)`. calendarBuddy will be a pure resource server. Both product UIs proxy `/api/auth/*` to authBuddy over the `homebuddy` Docker network.

## Decisions

### Accounts and tokens

`accounts(id uuid, email, email_normalized unique, password_hash, created_at, updated_at)`; `email_normalized` is the trimmed lower-case email. Register/login keep finanzBuddy's validation (email format, password 8–72 UTF-8 bytes), 201/409/400 for register and 200/401 for login, and the `{ token, email }` response. Tokens are RS256 with header `kid`, claims `iss=homebuddy-auth`, `sub`, `email`, `aud=["finanzbuddy","calendarbuddy"]`, `iat`, `exp` (`JWT_EXPIRATION_MS`, default 24 h).

Alternative: per-audience tokens requested by each UI. A shared audience list is simpler while the family account is meant to open every service; it can be narrowed later.

### Key management and rotation

Signing keys are RSA 2048+ PEMs supplied by environment/secret files: `AUTH_SIGNING_KEY` (current) and optional `AUTH_PREVIOUS_PUBLIC_KEYS`. The JWKS publishes the current and previous public keys with stable `kid`s (SHA-256 thumbprints). Rotation: add new key as current, keep old public key published for at least the token lifetime, then remove it. Consumers refresh JWKS on an unknown `kid`. The service refuses to start without a valid signing key.

### Legacy import

An admin-only command (`java -jar auth-service.jar import-legacy --file users.csv`, run as a one-off container) reads `email,password_hash` exported from finanzBuddy, inserts accounts with new UUIDs, and skips emails that already exist (idempotent). It reports counts only, never hashes or emails. BCrypt hashes are reused as-is. `AUTH_REGISTRATION_ENABLED=false` keeps registration closed until the import and the finanzBuddy switch are done, so nobody can claim an existing finanzBuddy email first.

Alternative: authBuddy reading finanzBuddy's database directly. That crosses data ownership and requires database credentials across stacks.

### Abuse protection

Login and registration are limited per client IP and per email (in-memory sliding window, default 10 attempts per minute) and return 429 when exceeded. Failure responses never reveal whether an email exists.

### Deployment

Own compose stack: `postgres` (`authbuddy` database, volume) and `auth-service`, following the root `homebuddy-deployment` conventions. The external `homebuddy` network comes from the root `docker-compose.yml` or a documented `docker network create homebuddy`; `auth-service` joins it with alias `auth`. It is not published through Traefik; product UIs proxy to it. Local override ports from the root registry: API 8090, Postgres 5434.

## Risks / Trade-offs

- Single point of failure for sign-in -> existing tokens stay valid at consumers with cached JWKS; health checks and restart policy.
- Leaked signing key compromises all services -> keys only via secrets, rotation runbook documented.
- Email takeover during migration -> registration stays disabled until the import completes.

## Migration Plan

1. Create the `homebuddy` network and deploy authBuddy with registration disabled.
2. Export finanzBuddy users and run the import; verify counts.
3. Switch finanzBuddy to authBuddy (`adopt-homebuddy-identity`), then enable registration.
4. Deploy calendarBuddy.
5. Rollback before step 3 is just removing authBuddy; after step 3, roll back finanzBuddy first (its local hashes remain until explicitly dropped).
