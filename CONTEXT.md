# Context / Glossary

Domain vocabulary for Gravitee Access Management. Use these terms consistently in code, tickets,
and tests. See `docs/adr/` for decisions.

## Glossary

- **Entrypoint** — stored configuration owned by an organization, and optionally scoped to an
  environment (`environmentId`, nullable; populated per-environment in cloud mode by AM-7225). Holds
  a public `url`, `tags`, and a `defaultEntrypoint` flag. An entrypoint is *configuration*, not the
  mechanism that resolves a request's base URL. Do not conflate it with:
  - **request-time base-URL resolution** — how a running gateway derives the scheme/host/port for a
    given request, from the request plus `X-Forwarded-*` headers and the DataPlane gateway URL
    (`UriBuilderRequest`, `DomainReadService.buildUrl`). This does not read entrypoints.
  - **VirtualHost** — per-domain host/path routing configuration.

- **EntryPointManager** — an in-memory, self-refreshing cache of entrypoints, present in both the
  MAPI and the Gateway, that serves entrypoints by `organizationId` and by `environmentId` without a
  database query on lookup. See `docs/adr/0001-entrypoint-manager-cache.md`.
