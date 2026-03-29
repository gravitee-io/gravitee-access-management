# Automation API Walkthrough

This guide walks through using the AM Automation API to declaratively provision a multi-environment setup with Domains and Identity Providers — the kind of setup you'd drive from Terraform, a GKO CRD, or a CI/CD pipeline.

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

## Base URL

```
http://localhost:8093/management/automation
```

## API Pattern

All resources follow the same convention:

| Operation | Method | Path | Identifier |
|-----------|--------|------|------------|
| Create/Update | PUT | Collection (`/domains`) | `hrid` in request body |
| Read one | GET | Resource (`/domains/{hrid}`) | `hrid` in path |
| List all | GET | Collection (`/domains`) | — |
| Delete | DELETE | Resource (`/domains/{hrid}`) | `hrid` in path |

PUT is always on the **collection** endpoint. The `hrid` field in the body identifies which resource to create or update — no HRID in the PUT path.

---

## 1. Provision Environments

Create three environments within the default organization.

```bash
ORG="DEFAULT"
BASE="http://localhost:8093/management/automation"

# Dev
curl -s -X PUT "$BASE/organizations/$ORG/environments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "hrid": "dev",
    "name": "Development",
    "description": "Development environment for internal testing"
  }' | jq .

# Staging
curl -s -X PUT "$BASE/organizations/$ORG/environments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "hrid": "staging",
    "name": "Staging",
    "description": "Pre-production environment for QA"
  }' | jq .

# Prod
curl -s -X PUT "$BASE/organizations/$ORG/environments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "hrid": "prod",
    "name": "Production",
    "description": "Live production environment",
    "domainRestrictions": ["mycompany.com", "auth.mycompany.com"]
  }' | jq .
```

Verify:
```bash
curl -s "$BASE/organizations/$ORG/environments" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].name'
```

> **Idempotency:** Running any PUT again with the same body is safe — it updates the existing resource.

---

## 2. Create Security Domains

Each environment gets its own security domains. The `hrid` in the body is the unique identifier.

```bash
ENV="dev"

# Customer-facing auth domain
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "hrid": "customer-auth",
    "name": "Customer Authentication",
    "description": "Handles customer login, registration, and MFA",
    "enabled": true,
    "dataPlaneId": "default"
  }' | jq '{id, hrid, name}'

# Internal admin domain
curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "hrid": "internal-admin",
    "name": "Internal Admin",
    "description": "Employee SSO for back-office applications",
    "enabled": true,
    "dataPlaneId": "default"
  }' | jq '{id, hrid, name}'
```

Repeat for staging and prod:
```bash
for ENV in staging prod; do
  for DOMAIN_HRID in customer-auth internal-admin; do
    curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"hrid\": \"$DOMAIN_HRID\",
        \"name\": \"$DOMAIN_HRID\",
        \"description\": \"$DOMAIN_HRID domain for $ENV\",
        \"enabled\": true,
        \"dataPlaneId\": \"default\"
      }" | jq '{id, hrid}'
  done
done
```

List domains in an environment:
```bash
curl -s "$BASE/organizations/$ORG/environments/dev/domains" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].hrid'
```

Read a single domain:
```bash
curl -s "$BASE/organizations/$ORG/environments/dev/domains/customer-auth" \
  -H "Authorization: Bearer $TOKEN" | jq '{hrid, name, enabled}'
```

> **Deterministic IDs:** The domain ID is a UUID derived from the environment + HRID. Same inputs always produce the same ID.

---

## 3. Add Identity Providers

### Inline IDP (dev only — test users)

```bash
curl -s -X PUT "$BASE/organizations/$ORG/environments/dev/domains/customer-auth/identity-providers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "hrid": "dev-test-users",
    "name": "Dev Test Users",
    "type": "inline-am-idp",
    "configuration": "{\"users\":[{\"firstname\":\"Alice\",\"lastname\":\"Test\",\"username\":\"alice\",\"password\":\"P@ssword1\"},{\"firstname\":\"Bob\",\"lastname\":\"Test\",\"username\":\"bob\",\"password\":\"P@ssword1\"}]}"
  }' | jq '{id, name, type}'
```

### LDAP IDP (all environments)

```bash
for ENV in dev staging prod; do
  curl -s -X PUT "$BASE/organizations/$ORG/environments/$ENV/domains/internal-admin/identity-providers" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "hrid": "corporate-ldap",
      "name": "Corporate LDAP",
      "type": "ldap-am-idp",
      "configuration": "{\"contextSourceUrl\":\"ldap://ldap.'$ENV'.internal:389\",\"contextSourceBase\":\"dc=mycompany,dc=com\",\"contextSourceUsername\":\"cn=admin,dc=mycompany,dc=com\",\"contextSourcePassword\":\"secret\",\"userSearchFilter\":\"uid={0}\",\"userSearchBase\":\"ou=people\"}",
      "mappers": {
        "sub": "uid",
        "email": "mail",
        "name": "cn"
      }
    }' | jq '{id, name, type}'
done
```

List IDPs:
```bash
curl -s "$BASE/organizations/$ORG/environments/dev/domains/internal-admin/identity-providers" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].name'
```

Read a single IDP:
```bash
curl -s "$BASE/organizations/$ORG/environments/dev/domains/customer-auth/identity-providers/dev-test-users" \
  -H "Authorization: Bearer $TOKEN" | jq '{id, name, type}'
```

---

## 4. Update an Existing Resource

PUT again to the collection with the same `hrid`. The API finds the existing resource and updates it.

```bash
curl -s -X PUT "$BASE/organizations/$ORG/environments/dev/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "hrid": "customer-auth",
    "name": "Customer Authentication",
    "description": "Updated: now includes social login",
    "enabled": true
  }' | jq '{hrid, description}'
```

---

## 5. Tear Down a Resource

DELETE uses the HRID in the path.

```bash
# Remove an IDP
curl -s -X DELETE "$BASE/organizations/$ORG/environments/dev/domains/customer-auth/identity-providers/dev-test-users" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"

# Remove a domain
curl -s -X DELETE "$BASE/organizations/$ORG/environments/dev/domains/internal-admin" \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP %{http_code}\n"
```

---

## 6. OpenAPI Spec

Auto-generated from annotations, available without authentication:

```bash
curl -s http://localhost:8093/management/automation/openapi.json | jq .info
curl -s http://localhost:8093/management/automation/openapi.yaml | head -20
```

---

## Quick Reference

| Resource | PUT (create/update) | GET one | GET list | DELETE |
|----------|-------------------|---------|----------|--------|
| Environment | `PUT .../environments` | `GET .../environments/{hrid}` | `GET .../environments` | `DELETE .../environments/{hrid}` |
| Domain | `PUT .../domains` | `GET .../domains/{hrid}` | `GET .../domains` | `DELETE .../domains/{hrid}` |
| Identity Provider | `PUT .../identity-providers` | `GET .../identity-providers/{hrid}` | `GET .../identity-providers` | `DELETE .../identity-providers/{hrid}` |

All paths prefixed with `http://{host}/management/automation/organizations/{orgId}`.
