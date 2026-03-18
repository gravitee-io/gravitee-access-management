# OpenShell Policy Integration — Hackathon Plan

Allow Agent applications in Gravitee AM to store and serve an OpenShell sandbox policy (YAML).
Agents authenticate via `client_credentials`, fetch their policy from AM, and pass it to OpenShell at sandbox creation time.

---

## Overview

OpenShell sandboxes are ephemeral, isolated execution environments for AI agents. Today, sandbox policies (network rules, filesystem access, process identity) must be managed separately from the agent's identity. This integration closes that gap: the agent's sandbox policy lives alongside its OAuth client in Gravitee AM, versioned and access-controlled like any other application setting.

**Flow:**
1. Operator defines a YAML sandbox policy in the AM UI for an Agent application
2. When creating a sandbox, the agent authenticates with its own `client_credentials` to get a token
3. The agent fetches its policy from the AM gateway using that token
4. OpenShell enforces the policy when creating the sandbox

---

## Checkpoint 1 — Policy persists via PATCH ✅ DONE

### What was built

Added `openShellPolicy` field to the AM data model so policies can be stored alongside other agent settings in MongoDB.

**Files changed:**
- `gravitee-am-model/.../ApplicationAdvancedSettings.java` — added `private String openShellPolicy` with getter, setter, copy constructor support, and `copyTo(Client)` propagation
- `gravitee-am-model/.../oidc/Client.java` — added `private String openShellPolicy` (in-memory gateway representation)
- `gravitee-am-service/.../PatchApplicationAdvancedSettings.java` — added `Optional<String> openShellPolicy` + wired into `patch()` via `SetterUtils.safeSet()`

**Smoke test:**
```bash
# PATCH an agent application with a policy
curl -X PATCH http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"settings":{"advanced":{"openShellPolicy":"version: 1\nnetwork_policies:\n  github:\n    name: github\n    endpoints:\n      - host: api.github.com\n        port: 443\n"}}}'

# GET the application and confirm openShellPolicy is present in settings.advanced
curl http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId} \
  -H "Authorization: Bearer $TOKEN" | jq '.settings.advanced.openShellPolicy'
```

---

## Checkpoint 2 — Dedicated policy endpoints ✅ DONE

### What was built

Added three dedicated REST endpoints to `ApplicationResource.java` for clean, typed access to the policy field — no need to PATCH the full application object.

**Files changed:**
- `gravitee-am-management-api/.../ApplicationResource.java` — added:
  - `GET /openshell-policy` — returns stored YAML as `text/plain`, 400 if not set
  - `PUT /openshell-policy` — accepts raw YAML, validates parseability via SnakeYAML, stores via application patch
  - `DELETE /openshell-policy` — clears the field (sets to null)

**Smoke test:**
```bash
# PUT a policy
curl -X PUT http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId}/openshell-policy \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: text/plain" \
  --data-binary @policy.yaml
# → 204

# GET the policy back
curl http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId}/openshell-policy \
  -H "Authorization: Bearer $TOKEN"
# → YAML text

# DELETE
curl -X DELETE http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId}/openshell-policy \
  -H "Authorization: Bearer $TOKEN"
# → 204
```

---

## Checkpoint 3 — Gateway plugin serves the policy ✅ DONE

### What was built

Created a dedicated AM gateway plugin (`gravitee-am-gateway-handler-openshell`) that serves the policy publicly from the gateway — no management API token required. This follows the same architecture as the discovery and CIBA protocol plugins.

The gateway already has the policy in memory (via `ClientSyncService`) because `ApplicationAdvancedSettings.copyTo(Client)` propagates it during domain sync.

**Files created (new module):**
- `gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-openshell/`
  - `pom.xml` — handler module bundled directly into the gateway distribution
  - `OpenShellProtocol.java` — extends `Protocol<OpenShellConfiguration, OpenShellProvider>`
  - `OpenShellProvider.java` — mounts Vert.x router at `/{domain}/openshell-policy`
  - `resources/endpoint/OpenShellPolicyEndpoint.java` — looks up app by ID via `ClientSyncService`, returns YAML or 404
  - `constants/OpenShellConstants.java` — path constants

**Files changed:**
- `gravitee-am-gateway/.../VertxSecurityDomainHandler.java` — added `"openshell"` to PROTOCOLS list
- `gravitee-am-gateway/gravitee-am-gateway-handler/pom.xml` — added module entry
- `gravitee-am-gateway/.../gravitee-am-gateway-standalone-distribution/pom.xml` — added `gravitee-am-gateway-handler-openshell` as a bundled dependency so it is included in the gateway distribution at build time (no separate plugin ZIP deployment needed)

**Policy endpoint (no auth required — policy content is configuration, not secret):**
```
GET /{domain}/openshell-policy/{appId}
→ 200 + YAML text
→ 404 if app not found or no policy set
```

---

## Checkpoint 4 — UI editor tab ✅ DONE

### What was built

Angular component for editing the OpenShell policy, wired into the Agent application advanced settings page.

**Files created:**
- `gravitee-am-ui/.../advanced/openshell-policy/openshell-policy.component.ts`
- `gravitee-am-ui/.../advanced/openshell-policy/openshell-policy.component.html`
- `gravitee-am-ui/.../advanced/openshell-policy/openshell-policy.component.scss`

**Files changed:**
- `gravitee-am-ui/.../services/application.service.ts` — added `getOpenShellPolicy()`, `setOpenShellPolicy()`, `deleteOpenShellPolicy()`
- `gravitee-am-ui/.../app-routing.module.ts` — added `openshell-policy` route (AGENT type only)
- `gravitee-am-ui/.../app.module.ts` — declared `OpenShellPolicyComponent`
- `gravitee-am-ui/package.json` — added `yaml: ^2.8.2` as direct dependency

**Smoke test:**
1. Open an Agent application in the AM UI
2. Navigate to Advanced settings → OpenShell Policy
3. Paste a YAML policy in the editor, click Save → snackbar confirms
4. Reload the page — policy is still there
5. Click Clear — policy is removed

---

## Checkpoint 5 — UI summary + how-to tabs ✅ DONE

### What was built

Two additional tabs alongside the editor: a visual summary that parses the YAML and renders it in structured sections, and a "How to use" tab with copy-pasteable CLI commands for the full agent flow.

**Summary tab renders:**
- **Network Policies** — table per rule showing host, port(s), protocol, enforcement mode
- **Filesystem Policy** — workdir toggle, read-only path chips, read-write path chips
- **Process Identity** — run-as user/group

**How to use tab:**
```bash
# Step 1: Get an access token using agent client credentials
TOKEN=$(curl -s -X POST {gatewayUrl}/{domain}/oauth/token \
  -d "grant_type=client_credentials&client_id={clientId}&client_secret=YOUR_SECRET" \
  | jq -r '.access_token')

# Step 2: Start sandbox with policy fetched from AM
openshell sandbox create \
  --policy "{gatewayUrl}/{domain}/openshell-policy" \
  --policy-bearer "$TOKEN"
```

---

## Checkpoint 6 — OpenShell CLI supports URL-based policy loading ✅ DONE

### What was built

Extended the OpenShell Rust CLI to support fetching sandbox policies from HTTP/HTTPS URLs, enabling the agent-to-AM integration without any local file management.

**Files changed:**
- `crates/openshell-policy/Cargo.toml` — added `reqwest` with `blocking` feature
- `crates/openshell-policy/src/lib.rs` — `load_sandbox_policy()` now detects `http://`/`https://` prefix and performs a blocking GET instead of a filesystem read

**Before:**
```bash
openshell sandbox create --policy ./policy.yaml
```

**After:**
```bash
# Policy fetched directly from AM at sandbox creation time
openshell sandbox create \
  --policy "https://gateway.example.com/my-domain/openshell-policy/my-app-id" \
  --policy-bearer "$TOKEN"
```

---

## Out of Scope (PoC)

- JDBC / PostgreSQL support — MongoDB only
- Policy version history
- Policy schema validation beyond "is valid YAML"
- Policy templates / inheritance
- New MongoDB collections or indexes — stored in existing application document
- Unit/integration tests beyond basic smoke tests

---

## Key Files

| What | Where |
|------|-------|
| Advanced settings model | `gravitee-am-model/src/main/java/io/gravitee/am/model/application/ApplicationAdvancedSettings.java` |
| Client model (gateway sync) | `gravitee-am-model/src/main/java/io/gravitee/am/model/oidc/Client.java` |
| Patch advanced settings | `gravitee-am-service/src/main/java/io/gravitee/am/service/model/PatchApplicationAdvancedSettings.java` |
| Application REST resource | `gravitee-am-management-api/gravitee-am-management-api-rest/.../ApplicationResource.java` |
| Gateway plugin module | `gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-openshell/` |
| Gateway domain handler | `gravitee-am-gateway/.../VertxSecurityDomainHandler.java` |
| Angular app service | `gravitee-am-ui/src/app/services/application.service.ts` |
| Angular UI component | `gravitee-am-ui/src/app/domain/applications/application/advanced/openshell-policy/` |
| OpenShell policy loader | `crates/openshell-policy/src/lib.rs` |
