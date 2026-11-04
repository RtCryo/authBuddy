# Migrating finanzBuddy users to homeBuddy accounts

This runbook moves the existing finanzBuddy users to authBuddy **without changing their passwords**. It implements the migration plan of the change `add-identity-service` and the rollout order of the root change `establish-homebuddy-platform`.

The export file contains password hashes. Treat it as a secret: keep it out of Git and chat, restrict its permissions, and delete it when the import is done.

## Before you start

- The external Docker network `homebuddy` exists (`docker network create homebuddy`, or the root `docker-compose.yml`).
- authBuddy is deployed with **`AUTH_REGISTRATION_ENABLED=false`**. Until the import and the finanzBuddy switch are finished, nobody can register, so nobody can claim an existing finanzBuddy email first.
- finanzBuddy still runs its own sign-in. Do not switch it yet.

## 1. Export the finanzBuddy users

finanzBuddy stores users in `users(id, email, password_hash, ...)`. Emails are already lower-case and unique. Run the export inside the finanzBuddy database container, so the credentials stay in the container's environment:

```bash
docker exec finanzbuddy-postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\copy (select email, password_hash from users order by id) to stdout with csv header"' \
  > users.csv
chmod 600 users.csv
```

On Windows run this in Git Bash or PowerShell 7+. Windows PowerShell 5.1 writes redirected output as UTF-16, which the import rejects.

The file starts with the header `email,password_hash`. Note the number of users:

```bash
docker exec finanzbuddy-postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "select count(*) from users"'
```

## 2. Run the import

The import is an operator-only command. It starts the application without a web server and without signing keys, so it is never reachable over HTTP. It needs only the database settings.

From a built jar (run from `auth-service/`, with the datasource variables of `.env` exported):

```bash
java -jar target/auth-service.jar import-legacy --file=../users.csv
```

Both `--file=users.csv` and `--file users.csv` are accepted. Exit codes: `0` finished, `1` the file cannot be read, `2` the `--file` argument is missing.

The command prints counts only, never emails or hashes:

```text
import-legacy: created=42 skipped=0 invalid=0
```

| Count | Meaning |
|---|---|
| `created` | New accounts, each with a new UUID and the BCrypt hash unchanged |
| `skipped` | Emails that already exist (ignoring case), including repeated rows; the existing account is not touched |
| `invalid` | Rows with a malformed email or a hash that is not BCrypt; they are never printed, so check the source rows in finanzBuddy |

Running it again with the same file is safe: every row is reported as `skipped`.

## 3. Verify

- `created + skipped` equals the number of users exported. If `invalid` is not `0`, find the affected rows in the finanzBuddy `users` table and fix them there, then re-export and re-run; rows already imported are skipped.
- Sign in with a known finanzBuddy account against authBuddy and confirm `200` with a token:

  ```bash
  curl -s -X POST http://localhost:8090/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"email":"<known user>","password":"<known password>"}'
  ```

## 4. Clean up

Delete `users.csv` and any copy of it.

## 5. Switch finanzBuddy, then open registration

1. Roll out finanzBuddy's `adopt-homebuddy-identity` change. Its gateway links each local user to the imported account by email on the first sign-in, and keeps its data.
2. Set `AUTH_REGISTRATION_ENABLED=true` in authBuddy's `.env` and restart it.
3. Deploy calendarBuddy.

## Rollback

- **Before step 5.1**: stop and remove authBuddy. finanzBuddy is untouched and keeps working.
- **After step 5.1**: roll back finanzBuddy first. Its local password hashes remain until they are explicitly dropped, so users can sign in with their old passwords again. Do not remove the imported accounts before finanzBuddy is rolled back.
- A wrong or partial import needs no cleanup: fix the source data and run the import again; existing accounts are skipped, never overwritten.
