## 1. Workflows and release configuration

- [ ] 1.1 Replace `.github/workflows/auth-service.yml` with `ci.yml` (jobs `changes`, `auth-service`, `image`, `compose`, `openspec`, `ci`), and add `pr-title.yml`. Verify that a documentation-only pull request reports a green `ci` without running the Maven job and that a non-conventional title fails the title check.
- [ ] 1.2 Add `release.yml`, `release-please-config.json` (`bootstrap-sha`, `release-as: 1.0.0`, `extra-files` for `.env.example`), `.release-please-manifest.json`, and `CHANGELOG.md`; add `":semanticCommits"` to `renovate.json`. Verify with the first release pull request that it proposes `v1.0.0`.

## 2. Image and Compose

- [x] 2.1 Add the `VERSION` and `REVISION` build arguments and OCI labels to `auth-service/Dockerfile`. Verify with `docker build --build-arg VERSION=0.0.0 auth-service` and `docker inspect`.
- [x] 2.2 Switch `docker-compose.yml` to the `authbuddy-auth-service` image pinned by `AUTHBUDDY_VERSION`, move `build:` to `docker-compose.override.yml`, and add the variable to `.env.example`. Verify that `docker compose -f docker-compose.yml config` has no `build:` section, fails without the variable naming it, and that `docker compose up --build` tags `authbuddy-auth-service:dev`.

## 3. Documentation and specifications

- [x] 3.1 Update `docs/README.md`, `docs/ARCHITECTURE.md`, and the deployment notes (delivery flow, `AUTHBUDDY_VERSION`, rollback across migrations). Verify that the image name and variable match the root `docs/ARCHITECTURE.md` registry.
- [ ] 3.2 Run `openspec validate adopt-homebuddy-delivery --strict` and archive the change after the root umbrella tasks for this repository are done.
