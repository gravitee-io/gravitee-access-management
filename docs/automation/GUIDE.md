# Automation API Guide

The AM **Automation API** declaratively provisions Domains and the resources that live under them —
Identity Providers, Certificates and Reporters — the kind of setup you'd drive from Terraform, a GKO
CRD, or a CI/CD pipeline.

This guide is organised as **conventions first** (the rules that apply to every resource type), then a
compact **per-resource reference**. New resource types follow the same conventions, so read those once.

- Canonical machine-readable contract: [`openapi.yaml`](openapi.yaml) (also served live at
  `/automation/openapi.yaml`).

## Prerequisites

- Management API running with automation enabled:
  ```yaml
  # gravitee.yml
  http:
    api:
      automation:
        enabled: true
  ```
- An admin Bearer token (examples use `$TOKEN`):
  ```bash
  TOKEN=$(curl -s http://localhost:8093/management/auth/token \
    -u admin:adminadmin \
    -d "grant_type=password&username=admin&password=adminadmin" | jq -r '.access_token')
  ```

> **Organizations and environments are a prerequisite, not part of this API.** Every path is rooted at
> `/organizations/{orgId}/environments/{envId}/…`; provision the organization and environment via the
> standard Management API beforehand. The default install ships both with the id `DEFAULT`.

Base URL: `http://localhost:8093/automation`

---

# Conventions

## 1. The API pattern

Every resource type (Domain, Identity Provider, Certificate, Reporter) is managed individually and
follows the same convention:

| Operation | Method | Path | Identifier |
|-----------|--------|------|------------|
| Create/Update | PUT | Collection (e.g. `/domains`) | reference in request **body** (`key`) |
| Read one | GET | Resource (e.g. `/domains/{ref}`) | reference in **path** |
| List all | GET | Collection (e.g. `/domains`) | — |
| Delete | DELETE | Resource (e.g. `/domains/{ref}`) | reference in **path** |

PUT is always on the **collection** endpoint; the `key` field in the body identifies which resource to
create or update. Each PUT manages a single resource and never touches siblings you didn't mention.

## 2. Addressing a resource

A resource is addressed two ways. Both are accepted anywhere a reference appears — a path segment, the
PUT body `key`, and embedded cross-resource references (see §6).

### By automation `key` (the normal case)

Every Automation-API-managed resource carries a dedicated `key` — a stable, human-chosen identifier set
on create and immutable thereafter (PUTting a different `key` creates a *new* resource). `key` is
distinct from the mutable `name`: you can rename freely without changing identity.

`key` format: lowercase alphanumeric and hyphens, starting and ending with an alphanumeric character
(`^[a-z0-9]([a-z0-9-]*[a-z0-9])?$`), up to 255 characters.

> **Deterministic IDs:** the internal id is a UUID derived from the resource's scope (environment for
> domains, domain for children) folded with its `key`. The same inputs always produce the same id, which
> gives idempotent create-or-update without exposing internal ids.

### By internal id — `id:<internalUuid>` (brownfield)

To manage a **preexisting** resource the Automation API did *not* create — one created via the UI or
the Management API, which has a random internal id and no automation key — address it by its internal id
with the `id:` prefix, e.g. `id:94157683-f481-45a9-9576-83f48145a9a0`.

`id:` addressing:

- **Bypasses the `managedBy` visibility gate** — it reaches resources regardless of who manages them
  (whereas `key` addressing only ever sees `AUTOMATION_API` resources, see §4).
- **Is update-only.** GET / PUT / DELETE require the resource to already exist; a miss is `404`. There
  is **no create-by-id** — creation is always by `key`.
- **Does not adopt the resource.** A PUT by `id:` edits the resource's fields in place and leaves its
  `managedBy` and (absent) automation key untouched — the resource remains exactly as owned as before.
  In an `id:` PUT body the `key` field carries the `id:<uuid>` token itself; it is the addressing token,
  not a key to assign.
- **Is scope-checked.** A child addressed by `id:` must belong to the addressed domain, and a domain to
  the path environment; a cross-scope id is reported as `404` (a foreign id cannot be probed).

`key` and `id:` references can be mixed freely within one path, e.g.
`/domains/customer-auth/identities/id:<uuid>` (an automation domain containing a UI-created IdP you want
to edit), or `/domains/id:<uuid>/identities/dev-users`.

## 3. The `system` flag

A Certificate, Identity Provider or Reporter may be declared the domain's **system** (built-in default)
resource of its type with `"system": true`: the system certificate signs tokens for applications that
don't pin one, the system identity provider backs user registration, the system reporter captures audit
logs. A system identity provider additionally takes the conventional `default-idp-<domainId>` id so the
gateway's registration fallback resolves to it. At most one system resource per type per domain.

When `system` is `true`, only `key` is required; the resource is built from the corresponding
`domains.*.default.*` settings in `gravitee.yml` and the `name`, `type`, and `configuration` fields are
ignored.

> **`system` is immutable** — like `key`, it is an identity attribute fixed at creation. A PUT that
> flips `system` on an existing resource is **rejected with `400`**; delete and recreate to change it.

## 4. Automation-managed scope (visibility)

Every resource created via this API is stamped `managedBy = AUTOMATION_API`. By `key`, the Automation
API only sees, mutates, or deletes its **own** resources:

- Listing / fetching by `key` only succeeds for an automation-managed resource — UI-created resources
  are invisible.
- Declaring a `key` whose deterministic id collides with a non-automation resource is rejected.

The **`id:` exception:** addressing by internal id bypasses this gate so you *can* reach a non-automation
resource — but only if you already know its id. `id:`-reachable resources never appear in `list`
responses (which stay automation-only), so there is no enumeration leak.

> **Automation domains start empty.** Unlike domains created via the UI, an automation-managed domain is
> **not** seeded with a system identity provider, reporter, or certificate. Declare what you need
> explicitly — including the built-in defaults via the `system` flag.

## 5. PUT is full desired state (declarative)

A PUT body is the **complete desired state** — no partial updates. To change one thing: `GET`, edit the
returned document, `PUT` the whole document back. Consequences:

- **Required on every domain PUT:** `key`, `name`, `path`, `dataPlaneId`. `enabled` defaults to `true`.
  `dataPlaneId` is immutable after creation (part of the document but never re-applied).
- **Strict settings reconciliation:** a settings block omitted from the payload is reset to its
  domain-creation default — `null` for every block **except `oidc`**, which is reset to its standard
  default (it must never be null).
- `GET` then `PUT` of the same payload is a lossless, idempotent round-trip.

## 6. Cross-resource references

A domain references certificates and an identity provider by reference (`key` or `id:`), stored in
`saml.certificate`, `certificateSettings.fallbackCertificate`, and
`accountSettings.defaultIdentityProviderForRegistration`. References are **eventually consistent**: they
may point to a resource that does not exist yet (any apply order works), and the reference is echoed back
verbatim on `GET`. Use `id:<uuid>` here to reference a brownfield resource that has no key.

---

# Resource reference

All paths are prefixed with
`http://{host}/automation/organizations/{orgId}/environments/{envId}`.

| Resource | PUT (create/update) | GET one | GET list | DELETE |
|----------|---------------------|---------|----------|--------|
| Domain | `…/domains` | `…/domains/{ref}` | `…/domains` | `…/domains/{ref}` |
| Identity Provider | `…/domains/{ref}/identities` | `…/identities/{ref}` | `…/identities` | `…/identities/{ref}` |
| Certificate | `…/domains/{ref}/certificates` | `…/certificates/{ref}` | `…/certificates` | `…/certificates/{ref}` |
| Reporter | `…/domains/{ref}/reporters` | `…/reporters/{ref}` | `…/reporters` | `…/reporters/{ref}` |

`{ref}` is either an automation `key` or an `id:<internalUuid>` (§2). Resource-specific fields:

- **Domain** — `key`, `name`, `path`, `dataPlaneId` (required); `description`, `enabled`, `tags`,
  `vhosts`, and settings blocks (`oidc`, `accountSettings`, `saml`, …).
- **Identity Provider** — `key`, `name`, `type`, `configuration`; optional `mappers`, `roleMapper`,
  `groupMapper`, `domainWhitelist`; or `key` + `system: true`.
- **Certificate** — `key`, `name`, `type`, `configuration`; or `key` + `system: true`.
- **Reporter** — `key`, `name`, `type`, `configuration`, `enabled`; or `key` + `system: true`.

See [`openapi.yaml`](openapi.yaml) for the full field-level contract.

---

# Worked examples

### Create / update a domain

```bash
BASE="http://localhost:8093/automation/organizations/DEFAULT/environments/DEFAULT"

curl -s -X PUT "$BASE/domains" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "key": "customer-auth",
    "name": "Customer Authentication",
    "path": "/customer-auth",
    "dataPlaneId": "default",
    "enabled": true
  }' | jq '{key, name}'
```

### Add an identity provider, then reference it for registration

```bash
curl -s -X PUT "$BASE/domains/customer-auth/identities" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "key": "dev-test-users",
    "name": "Dev Test Users",
    "type": "inline-am-idp",
    "configuration": "{\"users\":[{\"firstname\":\"Alice\",\"lastname\":\"Test\",\"username\":\"alice\",\"password\":\"P@ssword1\"}]}"
  }' | jq '{key, name}'

# the reference can be set before or after the IdP exists (eventual consistency)
curl -s -X PUT "$BASE/domains" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "key": "customer-auth", "name": "Customer Authentication",
    "path": "/customer-auth", "dataPlaneId": "default",
    "accountSettings": { "defaultIdentityProviderForRegistration": "dev-test-users" }
  }' | jq '.accountSettings.defaultIdentityProviderForRegistration'
```

### Declare a system resource (built from `gravitee.yml`)

```bash
curl -s -X PUT "$BASE/domains/customer-auth/certificates" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "key": "default", "system": true }' | jq '{key, system}'
```

### Edit a brownfield resource by internal id

```bash
# a UI-created IdP, addressed by its internal id; this updates it in place without adopting it
curl -s -X PUT "$BASE/domains/customer-auth/identities" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "key": "id:94157683-f481-45a9-9576-83f48145a9a0",
    "name": "Renamed via Automation API",
    "type": "inline-am-idp",
    "configuration": "{\"users\":[]}"
  }' | jq '{name}'

# read it back by id
curl -s "$BASE/domains/customer-auth/identities/id:94157683-f481-45a9-9576-83f48145a9a0" \
  -H "Authorization: Bearer $TOKEN" | jq '{name, type}'
```

### Tear down

```bash
# delete one identity provider
curl -s -X DELETE "$BASE/domains/customer-auth/identities/dev-test-users" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"

# delete a whole domain (cascades to its identity providers, certificates and reporters)
curl -s -X DELETE "$BASE/domains/customer-auth" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"
```

> **System reporter deletion:** the Automation API may delete a system reporter it **owns**
> (`managedBy = AUTOMATION_API`), including a domain's default. A reporter reached by `id:` that it does
> **not** own keeps the platform's "system reporter cannot be deleted" guard, so an `id:` delete of a
> brownfield domain's system reporter is rejected rather than silently removing the domain's audit sink.

---

# OpenAPI spec

Auto-generated from annotations, served without authentication:

```bash
curl -s http://localhost:8093/automation/openapi.json | jq .info
curl -s http://localhost:8093/automation/openapi.yaml | head -20
```
