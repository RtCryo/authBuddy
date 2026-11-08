# Proposal

## Why

The root change [`add-homebuddy-ci-and-releases`](../../../../openspec/changes/add-homebuddy-ci-and-releases/proposal.md) defines the root capability `homebuddy-delivery`: one CI workflow with a required `ci` gate, Conventional Commits, release-please releases, and production Compose files that run released images. authBuddy checks only what its path-filtered `auth-service.yml` selects, so no check can be required on `main`; it has no release, so production builds `auth-service` from whatever source is checked out; and it ships no image that operators can pin.

## What Changes

- Replace `.github/workflows/auth-service.yml` with `ci.yml` following the root capability `homebuddy-delivery`: change detection, Spotless and `./mvnw verify` for `auth-service`, an image build without push, validation of the production and development Compose configuration, `openspec validate --all --strict` pinned to `@fission-ai/openspec@1.13.2`, and the `ci` gate.
- Add `pr-title.yml` (Conventional Commits title check), `release.yml` (release-please, image publication, release notes), `release-please-config.json`, `.release-please-manifest.json`, and `CHANGELOG.md`; add `":semanticCommits"` to `renovate.json`.
- Publish one image per release: `ghcr.io/rtcryo/authbuddy-auth-service`, tagged `X.Y.Z` and `X.Y`, with OCI labels and provenance. `auth-service/Dockerfile` accepts `VERSION` and `REVISION` build arguments.
- `docker-compose.yml` runs `auth-service` from that image, pinned by `AUTHBUDDY_VERSION`; `docker-compose.override.yml` builds it from source and tags it `authbuddy-auth-service:dev`. `.env.example` documents `AUTHBUDDY_VERSION`.
- **BREAKING (deployment)**: `docker compose -f docker-compose.yml up -d` no longer builds; it pulls the version named in `.env` and fails naming `AUTHBUDDY_VERSION` when it is missing.
- Document the delivery flow in `docs/` and `docs/ARCHITECTURE.md`.

## Capabilities

### New Capabilities

- `delivery`: authBuddy's deployable component, image name, checks per component, and release configuration, as the instance of root `homebuddy-delivery` for this repository.

### Modified Capabilities

- `compose-deployment`: production runs the released `auth-service` image pinned by `AUTHBUDDY_VERSION`; local development builds from source.

## Impact

- `.github/workflows/`, `release-please-config.json`, `.release-please-manifest.json`, `CHANGELOG.md`, `renovate.json`, `docker-compose.yml`, `docker-compose.override.yml`, `.env.example`, `auth-service/Dockerfile`, `docs/`.
- GitHub settings: squash merge only, required checks `ci` and the title check on `main`, secret `RELEASE_PLEASE_TOKEN`, public and linked GHCR package (configured in the root umbrella change's rollout).
- Rollout order: authBuddy's first release `v1.0.0` runs in production before the product services are redeployed from their own releases.
