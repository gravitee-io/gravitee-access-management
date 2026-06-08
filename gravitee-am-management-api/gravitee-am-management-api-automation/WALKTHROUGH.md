# Automation API Walkthrough

This guide walks through using the AM Automation API to declaratively provision
Domains and the resources that live under them — Identity Providers, Certificates and
Reporters — the kind of setup you'd drive from Terraform, a GKO CRD, or a CI/CD pipeline.

## Prerequisites

- Management API running with automation enabled:
  ```yaml
  # gravitee.yml
  http:
    api:
      automation:
        enabled: true
  ```
- An admin Bearer token (all examples below use `$TOKEN`)

```bash
TOKEN=$(curl -s http://localhost:8093/management/auth/token \
  -u admin:adminadmin \
  -d "grant_type=password&username=admin&password=adminadmin" | jq -r '.access_token')
```

> **Organizations and environments are a prerequisite, not part of this API.**
> The Automation API operates *within* an existing organization and environment — it
> does **not** create, update, or delete them. Every path is rooted at
> `/organizations/{orgId}/environments/{envId}/…`; provision the organization and
> environment via the standard Management API beforehand. The default install ships an
> organization and environment both with the id `DEFAULT`, used throughout this guide.

## Base URL

```
http://localhost:8093/management/automation
```

## API Pattern

Every resource type (Domain, Identity Provider, Certificate, Reporter) is managed
individually and follows the same convention:

| Operation | Method | Path | Identifier |
|-----------|--------|------|------------|
| Create/Update | PUT | Collection (e.g. `/domains`) | `key` in request body |
| Read one | GET | Resource (e.g. `/domains/{key}`) | `key` in path |
| List all | GET | Collection (e.g. `/domains`) | — |
| Delete | DELETE | Resource (e.g. `/domains/{key}`) | `key` in path |

PUT is always on the **collection** endpoint. The `key` field in the body identifies
which resource to create or update — no key in the PUT path. Each PUT manages a single
resource; it never deletes sibling resources you didn't mention.

### Addressing: the `key`

Every Automation-API-managed resource (Domain, Identity Provider, Certificate, Reporter)
carries a dedicated `key` — a stable, human-chosen identifier set on create and immutable
thereafter (PUTting a different `key` creates a new resource). `key` is distinct from the
mutable `name`: you can rename a resource freely without changing its identity.

`key` format: lowercase alphanumeric and hyphens, starting and ending with an
alphanumeric character (`^[a-z0-9]([a-z0-9-]*[a-z0-9])?$`).

> **Deterministic IDs:** the internal resource id is a UUID derived from the resource's
> scope (environment for domains, domain for identity providers, certificates and
> reporters) folded with its `key`. The same inputs always produce the same id, which
> gives idempotent create-or-update without exposing internal ids.

### The `system` flag

A Certificate, Identity Provider or Reporter may be declared the domain's **system**
(built-in default) resource of its type with `"system": true`. The system certificate
signs tokens for applications that don't pin one, the system identity provider backs user
registration, the system reporter captures audit logs. A system identity provider
additionally adopts the conventional `default-idp-<domainId>` id so the gateway's
registration fallback resolves to it. There can be at most one system resource per type
per domain. Unlike via the Management API, the Automation API can freely GET and DELETE
its system resources — no system guard restricts it.

When `system` is `true`, only `key` is required; the resource is built from the
corresponding `domains.*.default.*` settings in `gravitee.yml` and the `name`, `type`,
and `configuration` fields in the payload are ignored.

> **`system` is immutable** — like `key`, it is an identity attribute fixed at creation
> (for an identity provider it co-determines the internal id). A PUT that flips `system`
> on an existing resource is **rejected with `400`**, not silently ignored; delete and
> recreate the resource to change it. A `GET → PUT` round-trip of an unmodified document is
> therefore still lossless.

### Automation-managed scope

Every resource created via this API is stamped `managedBy = AUTOMATION_API`. The
Automation API only sees, mutates, or deletes its own resources:

- Listing / fetching a domain, identity provider, certificate or reporter only succeeds
  for an automation-managed one — UI-created resources are invisible.
- Declaring a `key` whose deterministic id collides with a non-automation resource is
  rejected.

> **Automation domains start empty.** Unlike domains created via the UI, an
> automation-managed domain is **not** seeded with a system identity provider, a system
> reporter, or a system certificate. Declare what you need explicitly — including, if you
> want them, the built-in defaults themselves via the `system` flag (see above).

This makes adoption safe in environments that already host UI-created resources.

### PUT is full desired state (declarative)

A PUT body is the **complete desired state** of the resource — there are no partial
updates and no implicit/derived values. To change one thing: `GET`, edit the returned
document, `PUT` the whole document back. Consequences:

- **Required on every domain PUT:** `key`, `name`, `path`, `dataPlaneId`. `enabled`
  defaults to `true` if omitted. `dataPlaneId` is immutable after creation — it is part
  of the full document but is never re-applied on update.
- **Strict settings reconciliation:** any settings block omitted from the payload is
  reset to its domain-creation default — `null` (feature off) for every settings block
  **except `oidc`**, which is reset to its standard default (it must never be null).
- **References are eventually consistent:** the certificate / identity-provider keys a
  domain references (`saml.certificate`, `certificateSettings.fallbackCertificate`,
  `accountSettings.defaultIdentityProviderForRegistration`) need **not** already exist —
  they may point to a resource you create later, in any order. The key is stored verbatim
  so a `GET` echoes it back even before the target exists.
- `GET` then `PUT` of the same payload is a lossless, idempotent round-trip.

---

## 1. Create Security Domains

The `key` in the body is the unique identifier within the environment.

```bash
ORG="DEFAULT"
ENV="DEFAULT"
BASE="http://localhost:8093/management/automation"

# Customer-facing auth domain
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "customer-auth",
    "name": "Customer Authentication",
    "description": "Handles customer login, registration, and MFA",
    "path": "/customer-auth",
    "enabled": true,
    "dataPlaneId": "default"
  }' | jq '{key, name}'

# Internal admin domain
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "internal-admin",
    "name": "Internal Admin",
    "description": "Employee SSO for back-office applications",
    "path": "/internal-admin",
    "enabled": true,
    "dataPlaneId": "default"
  }' | jq '{key, name}'
```

List domains in the environment:
```bash
curl -s "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].key'
```

Read a single domain:
```bash
curl -s "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth" \
  -H "Authorization: Bearer $TOKEN" | jq '{key, name, enabled}'
```

---

## 2. Identity Providers — a resource under the domain

Identity providers are managed as their own resource under a domain, at
`/domains/{domainKey}/identities`. Each IdP is identified by its `key`. This
keeps payloads small: manage one IdP at a time without re-sending the whole domain.

### Inline IDP (test users)

```bash
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/identities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "dev-test-users",
    "name": "Dev Test Users",
    "type": "inline-am-idp",
    "configuration": "{\"users\":[{\"firstname\":\"Alice\",\"lastname\":\"Test\",\"username\":\"alice\",\"password\":\"P@ssword1\"},{\"firstname\":\"Bob\",\"lastname\":\"Test\",\"username\":\"bob\",\"password\":\"P@ssword1\"}]}"
  }' | jq '{key, name}'
```

### LDAP IDP with mappers

```bash
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/internal-admin/identities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "corporate-ldap",
    "name": "Corporate LDAP",
    "type": "ldap-am-idp",
    "configuration": "{\"contextSourceUrl\":\"ldap://ldap.internal:389\",\"contextSourceBase\":\"dc=mycompany,dc=com\",\"contextSourceUsername\":\"cn=admin,dc=mycompany,dc=com\",\"contextSourcePassword\":\"secret\",\"userSearchFilter\":\"uid={0}\",\"userSearchBase\":\"ou=people\"}",
    "mappers": {
      "sub": "uid",
      "email": "mail",
      "name": "cn"
    }
  }' | jq '{key, name}'
```

### System identity provider

When `system` is `true`, only `key` is required; the IDP is built from `domains.identities.default.*` in `gravitee.yml`:

```bash
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/identities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "default",
    "system": true
  }' | jq '{key, system}'
```

List / read / delete identity providers under a domain:
```bash
curl -s "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/identities" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].key'

curl -s "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/identities/dev-test-users" \
  -H "Authorization: Bearer $TOKEN" | jq '{key, name, type}'

curl -s -X DELETE "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/identities/dev-test-users" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"
```

### Account settings referencing an identity provider

`accountSettings.defaultIdentityProviderForRegistration` references an identity provider
by its `key`. The reference is **eventually consistent**: it may be `null`, or it may name
an IdP that does not exist yet — the request succeeds either way and the key round-trips on
`GET`. The reference simply resolves once the IdP exists. (Equally, you can leave it unset
and rely on a `system: true` identity provider, which the gateway uses for registration
automatically.)

```bash
# the reference can be set before or after the IdP is created — either order works:
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "customer-auth",
    "name": "Customer Authentication",
    "path": "/customer-auth",
    "dataPlaneId": "default",
    "accountSettings": {
      "defaultIdentityProviderForRegistration": "dev-test-users"
    }
  }' | jq '.accountSettings.defaultIdentityProviderForRegistration'
```

---

## 3. Certificates — a resource under the domain, referenced by SAML

Certificates are managed as their own resource under a domain, at
`/domains/{domainKey}/certificates`, identified by `key`. Domain settings reference a
certificate by its `key`.

```bash
# create a SAML signing certificate
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/certificates" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "saml-signing",
    "name": "SAML signing certificate",
    "type": "javakeystore-am-certificate",
    "configuration": "{...}"
  }' | jq '{key, name}'

# mark a certificate as the domain system certificate (signs application tokens)
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/certificates" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "default",
    "system": true
  }' | jq '{key, system}'
```

Reference a certificate from the domain's SAML / fallback settings by `key`. The reference
is eventually consistent — it may name a certificate you create before *or* after this PUT:

```bash
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "customer-auth",
    "name": "Customer Authentication",
    "path": "/customer-auth",
    "dataPlaneId": "default",
    "saml": {
      "enabled": true,
      "entityId": "https://auth.mycompany.com",
      "certificate": "saml-signing"
    }
  }' | jq '{key, saml}'
```

List / read / delete certificates under a domain:
```bash
curl -s "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/certificates" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].key'

curl -s -X DELETE "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/certificates/saml-signing" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"
```

---

## 4. Reporters — a resource under the domain

Reporters are managed as their own resource under a domain, at
`/domains/{domainKey}/reporters`, identified by `key`. Mark one `system: true` to make it
the domain's system audit reporter (only `key` is required; config comes from
`gravitee.yml`).

```bash
# custom reporter
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/reporters" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "audit-kafka",
    "name": "Audit Kafka reporter",
    "type": "reporter-am-kafka",
    "configuration": "{\"bootstrapServers\":\"kafka:9092\",\"topic\":\"am-audit\",\"acks\":\"1\"}"
  }' | jq '{key, name}'

# system reporter (built from gravitee.yaml)
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/reporters" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "default",
    "system": true
  }' | jq '{key, system}'

curl -s "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/reporters" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].key'

curl -s -X DELETE "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/reporters/audit-kafka" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"
```

---

## 5. Update an Existing Resource

PUT again to the collection with the same `key`. Because PUT carries the **full desired
state**, send the complete document (not just the changed field) — re-include the
required fields and any settings you want to keep, or they will be reset to their
domain-creation default (see "PUT is full desired state" above).

```bash
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "customer-auth",
    "name": "Customer Authentication",
    "description": "Updated: now includes social login",
    "path": "/customer-auth",
    "enabled": true,
    "dataPlaneId": "default"
  }' | jq '{key, description}'
```

> The robust pattern is **GET → edit → PUT**: fetch the current document, change what
> you need, and PUT the whole thing back.

---

## 6. Tear Down a Resource

DELETE uses the `key` in the path. Deleting a domain removes its identity providers,
certificates and reporters.

```bash
# remove a single identity provider
curl -s -X DELETE "$BASE/organizations/$ORG/environments/$ENV/domains/customer-auth/identities/dev-test-users" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"

# remove a whole domain
curl -s -X DELETE "$BASE/organizations/$ORG/environments/$ENV/domains/internal-admin" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"
```

---

## 7. OpenAPI Spec

Auto-generated from annotations, available without authentication:

```bash
curl -s http://localhost:8093/management/automation/openapi.json | jq .info
curl -s http://localhost:8093/management/automation/openapi.yaml | head -20
```

---

## Quick Reference

| Resource | PUT (create/update) | GET one | GET list | DELETE |
|----------|---------------------|---------|----------|--------|
| Domain | `PUT .../domains` | `GET .../domains/{key}` | `GET .../domains` | `DELETE .../domains/{key}` |
| Identity Provider | `PUT .../domains/{key}/identities` | `GET .../domains/{key}/identities/{identityKey}` | `GET .../domains/{key}/identities` | `DELETE .../domains/{key}/identities/{identityKey}` |
| Certificate | `PUT .../domains/{key}/certificates` | `GET .../domains/{key}/certificates/{key}` | `GET .../domains/{key}/certificates` | `DELETE .../domains/{key}/certificates/{key}` |
| Reporter | `PUT .../domains/{key}/reporters` | `GET .../domains/{key}/reporters/{key}` | `GET .../domains/{key}/reporters` | `DELETE .../domains/{key}/reporters/{key}` |

All paths are prefixed with
`http://{host}/management/automation/organizations/{orgId}/environments/{envId}`.

Domains, identity providers, certificates and reporters are all addressed by their `key`.
Only resources whose `managedBy = AUTOMATION_API` are visible to this API — UI-created
resources are never read, mutated, or deleted by it.
