# Proposal

## Why

The root change [`isolate-product-apis`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/archive/2026-11-06-isolate-product-apis/proposal.md) removes the product APIs from the shared `homebuddy` network. Each API then reaches authBuddy over its own internal-only identity network (`finanzbuddy-identity`, `calendarbuddy-identity`) as defined by the root capability `homebuddy-deployment`. authBuddy must be a member of those networks before any API leaves `homebuddy`, or the APIs can no longer fetch the JWKS.

## What Changes

- `auth-service` joins the external networks `finanzbuddy-identity` and `calendarbuddy-identity` with the alias `auth`, in addition to `homebuddy` and the stack's default network.
- Document the one-time `docker network create --internal` commands for the identity networks in the deployment documentation and the runbook prerequisites.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `compose-deployment`: the network requirement now covers the identity networks.

## Non-goals

- Changing the JWKS URI, token format, or endpoints. The alias `auth` is the same on every network.
- Restricting which paths an API may call on authBuddy.

## Impact

- `docker-compose.yml`, `docs/ARCHITECTURE.md`, `docs/MIGRATION.md`.
- Hosts must create both identity networks before starting authBuddy.
