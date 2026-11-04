# Spec Delta

## Purpose

Moves existing finanzBuddy users to homeBuddy accounts without changing their passwords.

## ADDED Requirements

### Requirement: Legacy finanzBuddy users can be imported once and safely
An operator-only import SHALL read rows of email and BCrypt hash exported from finanzBuddy, create an account with a new UUID for each email not yet present ignoring case, reuse the hash unchanged, and skip existing or malformed rows. The import SHALL be idempotent, SHALL report only counts of created, skipped, and invalid rows, and SHALL NOT be reachable over HTTP.

#### Scenario: Import existing users
- **WHEN** the operator imports a file with 3 valid finanzBuddy users
- **THEN** 3 accounts exist and each user signs in with their previous finanzBuddy password

#### Scenario: Run the import twice
- **WHEN** the same file is imported again
- **THEN** no account is created or changed and all rows are reported as skipped

#### Scenario: Malformed row
- **WHEN** a row has an invalid email or a hash that is not BCrypt
- **THEN** the row is counted as invalid without printing its content and other rows are imported
