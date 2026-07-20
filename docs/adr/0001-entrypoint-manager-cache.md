# 1. In-memory EntryPointManager with event-driven cache

Date: 2026-07-20

## Status

Accepted

## Context

An `Entrypoint` is stored configuration owned by an organization (and, since AM-7225, optionally an
environment): a public URL, tags, and a default flag. Entrypoints are distinct from how AM resolves
a base URL at request time (which uses the incoming request plus `X-Forwarded-*` headers and the
DataPlane gateway URL) and from `VirtualHost` routing. Today entrypoints are read from the database
only in management/console flows.

AM-7226 asks for a manager that loads all entrypoints an instance is responsible for into memory on
startup, scoped by the organizations/environments configured in `gravitee.yaml`, and exposes lookups
by `organizationId` and `environmentId`, so lookups do not hit the database on the hot path. AM-7225
(PR #8253) adds the `environmentId` field and `findByEnvironment` repository method this builds on,
and in cloud mode recreates an environment's entrypoints on every Cockpit `EnvironmentCommand`.

## Decision

- Introduce `EntryPointManager`, an in-memory cache with two indexes (by organization, by
  environment) exposing `findByOrganizationId` and `findByEnvironmentId`. Entrypoints with no
  environment are reachable by organization only.
- Wire it in **both** the MAPI and the Gateway. This ticket is **groundwork**: it delivers the
  manager, cache, refresh and lookups only. **No request-time consumer is rewired.**
- Load on startup scoped to the configured organizations/environments (read from node metadata,
  the same signal the gateway already uses to decide which domains it serves); load all
  organizations when no scope is configured.
- Keep the cache **live in both planes**, because cloud mode mutates entrypoints at runtime:
  - MAPI refreshes via the existing in-process event + lazy-staleness pattern (as
    `IdentityProviderManager` does).
  - The Gateway refreshes via the existing cross-process sync: a new `ENTRYPOINT` sync event flows
    through the events table and the gateway sync service into the in-process event bus.
- The entrypoint sync event is **cross-cutting (not domain-scoped)**; on the Gateway the manager is
  a singleton in the standalone/container context so it receives all cross-domain events.
- Because the gateway event poll filters the events table by `dataPlaneId`, the entrypoint event is
  tagged with a `dataPlaneId` resolved for its environment, defaulting to the platform default when
  unresolvable.

## Consequences

- The Gateway gains its first organization/environment-scoped cache; every other gateway manager is
  domain-scoped.
- **Out of scope / limitations:** multi-data-plane (sharded) deployments where an environment spans
  data planes; and live gateway refresh for environment-less (non-cloud, organization-only)
  entrypoints, which are covered by the startup load only. Both are follow-ups if a need arises.
- A future request-time consumer will validate the cache/refresh shape and may remove the need for
  the `dataPlaneId` tagging.

## References

- AM-7226 (this work) — `.scratch/am-7226-entrypoint-manager/PRD.md`
- AM-7225 / PR #8253 — adds `Entrypoint.environmentId` and `EntrypointRepository.findByEnvironment`
