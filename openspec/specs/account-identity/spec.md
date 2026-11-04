# Account Identity Specification

## Purpose

Provides the shared homeBuddy account used by every homeBuddy service.

## Requirements

### Requirement: Families register a homeBuddy account
When registration is enabled, the system SHALL create an account from an email unique ignoring case and surrounding whitespace and a password of 8 to 72 UTF-8 bytes, store only a BCrypt hash, and respond 201 with `{ token, email }`. A duplicate email SHALL return 409 and invalid input 400. While registration is disabled, `POST /auth/register` SHALL return 403 and create nothing.

#### Scenario: Register
- **WHEN** a visitor registers `family@example.com` with a valid password
- **THEN** an account is created and a token is returned

#### Scenario: Duplicate email
- **WHEN** a visitor registers ` Family@Example.com ` after `family@example.com` exists
- **THEN** the system returns 409 and creates no account

#### Scenario: Registration disabled
- **WHEN** registration is disabled during migration and a visitor registers
- **THEN** the system returns 403 and creates no account

### Requirement: Accounts sign in and receive tokens valid for all homeBuddy services
`POST /auth/login` SHALL return `{ token, email }` for correct credentials and 401 otherwise without revealing which part was wrong. Tokens SHALL conform to the root `homebuddy-identity-contract`: RS256 JWTs with a key id, issuer `homebuddy-auth`, the account UUID as subject, the email, audiences `finanzbuddy` and `calendarbuddy`, and a configurable expiry. Repeated failed attempts SHALL be rate limited with 429.

#### Scenario: Sign in
- **WHEN** an account signs in with correct credentials
- **THEN** the returned token is accepted by finanzBuddy and calendarBuddy

#### Scenario: Wrong password
- **WHEN** a sign-in uses a wrong password or unknown email
- **THEN** the system returns 401 with the same response for both cases

#### Scenario: Too many attempts
- **WHEN** more than the configured number of sign-in attempts arrive for one email within a minute
- **THEN** further attempts return 429 until the window passes

### Requirement: Signing keys are published and rotatable
`GET /.well-known/jwks.json` SHALL publish the public keys of the current signing key and every configured previous key, each with its key id, and SHALL NOT expose private key material. The service SHALL refuse to start without a valid signing key.

#### Scenario: Verify a token with the JWKS
- **WHEN** a consumer fetches the JWKS and verifies a freshly issued token
- **THEN** verification succeeds using the key named by the token's key id

#### Scenario: Rotate keys
- **WHEN** a new signing key becomes current and the old public key is kept as previous
- **THEN** tokens signed with either key verify until the old key is removed
