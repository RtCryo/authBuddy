# Design

## Context

See proposal.md. `auth-service` is a Spring Boot service with the actuator on port 8080 exposing only `health`, and a stateless security chain that denies everything except health, JWKS, register, and login. Product UIs proxy only `/api/auth/` to `http://auth:8080/auth/`, so actuator paths are not reachable from browsers. The root design is in [`add-homebuddy-monitoring`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-monitoring/design.md).

## Decisions

- **Micrometer Prometheus registry on the main port.** Add `micrometer-registry-prometheus`, expose `health,prometheus`, and permit only `GET /actuator/prometheus` besides the health rules. A separate management port would not change reachability: other members of `homebuddy` could still reach it. The metrics carry only bounded aggregates.
- **Count at the decision points.** Outcomes are recorded where they are decided: `AuthController.login` (`success`, `invalid_credentials` on a 401, `rate_limited` when the limiter rejects), `AuthController.register` (`success`, `rejected` for a conflict or a rate-limited attempt; input rejected by validation never reaches the controller and is not counted, `disabled` when registration is off), and `TokenService.issue` for tokens. Counters are created once per outcome so the series set is fixed.
- **No identifiers in labels.** The only business label is `outcome`; the HTTP metrics keep Micrometer's default labels, which use the URI template, not raw paths or request data.
- **Metrics network.** `auth-service` joins `authbuddy-metrics` (external, `--internal`), declared like the identity networks. The central Prometheus is its only other member.

## Risks / Trade-offs

- Members of `homebuddy` could read `/actuator/prometheus`. Accepted: the data is aggregate counters.
- Renaming a metric requires updating the root dashboard in an umbrella change.

## Migration Plan

Create `authbuddy-metrics` on every host, then deploy. Rollback by removing the network entry and the endpoint exposure; nothing else depends on it.
