# Spec Delta

## Purpose

Defines how authBuddy instantiates the root capability `homebuddy-delivery`: its deployable component, its image, and its checks.

## ADDED Requirements

### Requirement: authBuddy follows the homeBuddy delivery contract
authBuddy SHALL satisfy the root capability `homebuddy-delivery` (CI baseline, `ci` gate, Conventional Commits, release-please releases, image publication, production on released images) and SHALL NOT restate it. Its deployable component is `auth-service`; its image is `ghcr.io/rtcryo/authbuddy-auth-service`. Its CI SHALL, for `auth-service`, check formatting with Spotless and run `./mvnw verify`, and SHALL build the `auth-service` image without pushing it.

#### Scenario: Formatting violation
- **WHEN** a pull request changes a Java file in `auth-service` so that `spotless:check` fails
- **THEN** `ci` fails

#### Scenario: Documentation-only pull request
- **WHEN** a pull request changes only files under `docs/`
- **THEN** the Maven job does not run and `ci` is reported as successful

### Requirement: A release publishes the auth-service image
Each authBuddy release SHALL publish `ghcr.io/rtcryo/authbuddy-auth-service` with the tags `X.Y.Z` and `X.Y` as specified by the root capability `homebuddy-delivery`, built from `auth-service/Dockerfile` with the build arguments `VERSION` and `REVISION`.

#### Scenario: authBuddy releases v1.0.0
- **WHEN** the release pull request for `v1.0.0` is merged
- **THEN** `ghcr.io/rtcryo/authbuddy-auth-service:1.0.0` and `:1.0` exist with a provenance attestation, and the GitHub Release lists the image with its digest
