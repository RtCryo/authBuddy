# Design

## Context

authBuddy has one deployable component, `auth-service` (Spring Boot, Java 25, Maven wrapper, Spotless), and no UI. Its only workflow today is `auth-service.yml` with the jobs Code Style, Test, Compose configuration, and OpenSpec, all behind path filters. The shared decisions (workflow shape, release flow, image tags, token handling, Compose layout) are made in the root change `add-homebuddy-ci-and-releases` and its capability `homebuddy-delivery`; this design records only what is specific to authBuddy.

## Decisions

### 1. Jobs

`ci.yml` has the jobs `changes`, `auth-service` (Spotless check, then `./mvnw -B verify`; Docker is available for Testcontainers), `image` (build `auth-service` without push, with the GitHub Actions cache), `compose` (`docker compose -f docker-compose.yml config --quiet` and `docker compose config --quiet` with `POSTGRES_PASSWORD=ci-postgres-password` and `AUTHBUDDY_VERSION=0.0.0-ci`), `openspec`, and `ci`. The `auth-service` filter is `auth-service/**`; `compose` is selected by `docker-compose*.yml` and `.env.example`, and `openspec` by `openspec/**`. The workflow file and `release-please-config.json` select every job.

### 2. Image and Dockerfile

One image, `ghcr.io/rtcryo/authbuddy-auth-service`. The Dockerfile gains `ARG VERSION=dev` and `ARG REVISION=unknown` in the runtime stage with `LABEL org.opencontainers.image.version` and `org.opencontainers.image.revision`; the source label comes from `docker/metadata-action`.

### 3. Compose

`auth-service` gets `image: ghcr.io/rtcryo/authbuddy-auth-service:${AUTHBUDDY_VERSION:?Set AUTHBUDDY_VERSION to a released version (see .env.example)}` and loses `build:`. `docker-compose.override.yml` adds `build:` with context `./auth-service` and `image: authbuddy-auth-service:dev`. PostgreSQL and every network stay unchanged.

### 4. Release bootstrap

`bootstrap-sha` is the `main` commit that merges this change; `release-as: 1.0.0` yields `v1.0.0` and is removed afterwards in a `chore` pull request. `.env.example` carries `AUTHBUDDY_VERSION=1.0.0 # x-release-please-version` and is listed under `extra-files`.

## Risks / Trade-offs

- [The first pull from GHCR fails while the package is private] → Make the package public and linked to the repository right after the first release, before switching production.
- [A host's `.env` lacks the new variable] → Compose fails fast naming `AUTHBUDDY_VERSION`; the deployment notes list the new step.
