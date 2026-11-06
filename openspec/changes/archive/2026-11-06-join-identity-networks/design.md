# Design

## Context

See proposal.md for motivation. `auth-service` today joins `homebuddy` (alias `auth`) and the stack's default network. The topology and rationale are in the root [design](../../../../../openspec/changes/archive/2026-11-06-isolate-product-apis/design.md).

## Decisions

- **Declare the identity networks as `external: true` with explicit names.** Compose cannot create a network that only an API and authBuddy join, and the root contract requires operators to create them once with `--internal`. This also makes a missing network fail fast with its name.
- **Same alias `auth` on every network.** The JWKS URI stays unchanged for every consumer; each identity network holds one API, so the alias does not collide.
- **Stay on `homebuddy`.** The product UIs proxy `/api/auth/` there.

## Risks / Trade-offs

- A host that runs only some products still has to create every listed network. Creating an unused network costs nothing, and the documentation lists the commands.
- Adding a product service means adding one network entry here, in that service's umbrella change.

## Migration Plan

Additive: deploy this change after the networks exist and before any API leaves `homebuddy`. Rollback by removing the network entries; APIs on `homebuddy` keep working.
