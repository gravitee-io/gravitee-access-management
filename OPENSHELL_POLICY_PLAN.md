# OpenShell Policy Integration — Hackathon Plan

Allow Agent applications in Gravitee AM to store and serve an OpenShell sandbox policy (YAML).
Agents authenticate via `client_credentials`, fetch their policy from AM, and pass it to OpenShell at sandbox creation time.

---

## Checkpoint 1 — Policy persists via PATCH ✅ testable via curl

### Backend: Model

- [ ] `ApplicationAdvancedSettings.java` — add `private String openShellPolicy`
- [ ] `PatchApplicationAdvancedSettings.java` — add `private Optional<String> openShellPolicy` + apply logic

**Smoke test:**
```bash
# PATCH an agent application with a policy
curl -X PATCH https://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"settings":{"advanced":{"openShellPolicy":"version: 1\nnetwork_policies:\n  github:\n    name: github\n    endpoints:\n      - host: api.github.com\n        port: 443\n"}}}'

# GET the application and confirm openShellPolicy is present in settings.advanced
curl https://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId} \
  -H "Authorization: Bearer $TOKEN" | jq '.settings.advanced.openShellPolicy'
```

---

## Checkpoint 2 — Dedicated policy endpoints ✅ testable via curl

### Backend: REST Endpoints

Add three methods to `ApplicationResource.java`:

- [ ] `GET /openshell-policy` — returns stored YAML with `Content-Type: application/yaml`, 404 if not set, requires `APPLICATION[READ]`
- [ ] `PUT /openshell-policy` — accepts raw YAML body, validates it is parseable YAML, stores via application PATCH, requires `APPLICATION[UPDATE]`
- [ ] `DELETE /openshell-policy` — clears the field (sets to null), requires `APPLICATION[UPDATE]`

No new service class — read/write directly through `applicationService` using the existing patch flow.

**Smoke test:**
```bash
# PUT a policy
curl -X PUT https://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId}/openshell-policy \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/yaml" \
  --data-binary @examples/sandbox-policy-quickstart/policy.yaml

# GET the policy back as YAML
curl https://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId}/openshell-policy \
  -H "Authorization: Bearer $TOKEN"

# DELETE
curl -X DELETE https://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId}/openshell-policy \
  -H "Authorization: Bearer $TOKEN"
# → 204, then GET should return 404
```

---

## Checkpoint 3 — Agent can self-fetch its policy ✅ testable end-to-end

This validates the actual OpenShell integration contract: the agent authenticates with its own credentials and fetches its policy.

**Smoke test:**
```bash
# 1. Get a token using the agent's own client credentials
TOKEN=$(curl -s -X POST https://localhost:8092/{domain}/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id={agentClientId}&client_secret={agentClientSecret}" \
  | jq -r '.access_token')

# 2. Fetch the policy using that token
curl https://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/{domain}/applications/{appId}/openshell-policy \
  -H "Authorization: Bearer $TOKEN"
# → should return the YAML policy
```

---

## Checkpoint 4 — UI editor tab ✅ testable in browser

### Frontend: Service

- [ ] `application.service.ts` — add three methods:
  ```typescript
  getOpenShellPolicy(domainId: string, appId: string): Observable<string>
  setOpenShellPolicy(domainId: string, appId: string, yaml: string): Observable<void>
  deleteOpenShellPolicy(domainId: string, appId: string): Observable<void>
  ```

### Frontend: Component scaffold

- [ ] Create `gravitee-am-ui/src/app/domain/applications/application/advanced/openshell-policy/` directory
- [ ] `openshell-policy.component.ts` — loads policy on init, exposes save/clear
- [ ] `openshell-policy.component.html` — Editor tab with `<textarea>` for raw YAML + Save / Clear buttons
- [ ] `openshell-policy.component.spec.ts` — basic unit test
- [ ] Wire component into Advanced settings page — only shown when `application.type === 'AGENT'`

**Smoke test:**
1. Open an Agent application in the AM UI
2. Navigate to Advanced settings
3. Confirm "OpenShell Policy" section is visible
4. Paste a YAML policy, click Save
5. Reload the page — policy should still be there
6. Click Clear — policy removed

---

## Checkpoint 5 — UI summary tab ✅ fully demo-able

### Frontend: Visual breakdown

- [ ] Parse the stored YAML in the component (use `js-yaml` — already a transitive dep via `@a2a-js/sdk`)
- [ ] Add a Summary tab alongside the Editor tab:
  - Network policies table: rule name | host | port | protocol | enforcement
  - Filesystem section: read-only paths list, read-write paths list
- [ ] "How to use" snippet section — copy-pasteable CLI commands:
  ```bash
  # Fetch policy and create sandbox
  TOKEN=$(curl -s -X POST https://{gateway}/{domain}/oauth/token \
    -d "grant_type=client_credentials&client_id={clientId}&client_secret={clientSecret}" \
    | jq -r '.access_token')

  openshell sandbox create \
    --policy <(curl -sH "Authorization: Bearer $TOKEN" \
      https://{managementApi}/domains/{domain}/applications/{appId}/openshell-policy)
  ```
- [ ] Show empty state when no policy is configured yet

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
| Patch advanced settings | `gravitee-am-service/src/main/java/io/gravitee/am/service/model/PatchApplicationAdvancedSettings.java` |
| Application REST resource | `gravitee-am-management-api/gravitee-am-management-api-rest/src/main/java/io/gravitee/am/management/handlers/management/api/resources/organizations/environments/domains/ApplicationResource.java` |
| Angular app service | `gravitee-am-ui/src/app/services/application.service.ts` |
| Agent metadata component (reference) | `gravitee-am-ui/src/app/domain/applications/application/advanced/agent-metadata/` |
| OpenShell policy examples | `/Users/stuart.clark/workspace/OpenShell/examples/` |
