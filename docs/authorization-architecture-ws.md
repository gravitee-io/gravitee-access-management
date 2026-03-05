# Authorization Engine Architecture (WebSocket)

## Overview

```mermaid
graph LR
    UI["Management Console"]
    MAPI["Management API"]
    DB[("Database<br/>(Mongo / JDBC)")]
    SC["Sidecar<br/>Cedar Engine<br/>+ AuthZEN endpoint"]
    APP["Client Application"]

    subgraph GW["Gateway"]
        MGR["AuthorizationEngineManager"]
        PLUGIN["Sidecar Plugin<br/>AuthorizationEngineProvider"]
        MGR -->|"updateConfig(snapshot)"| PLUGIN
    end

    UI -->|"REST"| MAPI
    MAPI -->|"CRUD"| DB
    MAPI -->|"BundleEvent"| MGR
    MGR -->|"resolve bundle"| DB
    PLUGIN <-->|"WebSocket<br/>/_authz/ws"| SC
    APP -->|"POST /access/v1/evaluation"| SC
```

**Components:**
- **Management Console** -- Angular UI for managing policy sets, schemas, entity stores, bundles, and engine configuration
- **Management API** -- REST layer (Jersey) + service layer (RxJava) + audit logging
- **Database** -- MongoDB or JDBC (Postgres/MySQL/...) with versioned content tables
- **Gateway** -- `AuthorizationEngineManagerImpl` listens for `BundleEvent`, resolves bundle content into `ResolvedBundleSnapshot`, pushes to provider via `updateConfig(snapshot)`
- **Sidecar Plugin** -- `SidecarAuthorizationEngineProvider` implements `AuthorizationEngineProvider` API; manages WS sessions, broadcasts bundles, receives audit events
- **Sidecar** -- external process connecting via WebSocket; runs Cedar engine locally, exposes AuthZEN evaluation endpoint, reports audit events back to gateway
- **Client Application** -- sends authorization requests to the sidecar's `/access/v1/evaluation` endpoint

## WebSocket Protocol

The sidecar connects to the gateway via WebSocket. All communication flows over a single persistent connection per sidecar instance.

### Connection

```
ws://<gateway-host>:<gateway-port>/<domain-path>/_authz/ws
Header: X-API-Key: <apiKey>
```

The sidecar sends the API key via the `X-API-Key` HTTP header during the WebSocket upgrade handshake. The gateway's `SidecarAuthorizationEngineProvider` validates it using constant-time comparison (`MessageDigest.isEqual`), then upgrades the connection to WebSocket.

### Message Types

All messages are JSON with a `"type"` discriminator field.

| Type | Direction | Fields | Purpose |
|------|-----------|--------|---------|
| `bundle_check` | Sidecar → Gateway | `version` (int) | Request bundle if version differs |
| `bundle_current` | Gateway → Sidecar | — | Sidecar is up to date |
| `bundle_update` | Gateway → Sidecar | `version`, `policy`, `data`, `schema` | New bundle content |
| `audit_event` | Sidecar → Gateway | `event` (object) | Evaluation audit report |
| `error` | Gateway → Sidecar | `code`, `message` | Error notification |

### Message Flow

```mermaid
sequenceDiagram
    participant SC as Sidecar
    participant GW as Gateway

    SC->>GW: WebSocket connect /<domain-path>/_authz/ws (X-API-Key header)
    GW->>GW: Validate API key
    GW-->>SC: 101 Switching Protocols

    loop Every 10 seconds
        SC->>GW: bundle_check(version=0)
        alt Bundle version > sidecar version
            GW-->>SC: bundle_update(version=1, policy, data, schema)
            SC->>SC: Deploy to Cedar engine
        else Sidecar is current
            GW-->>SC: bundle_current
        end
    end

    Note over SC,GW: After each evaluation...
    SC->>GW: audit_event({decisionId, timestamp, decision, ...})
```

### Reconnection

On disconnect, the sidecar reconnects with exponential backoff:
- Initial delay: 1 second
- Doubles each attempt: 1s → 2s → 4s → 8s → 16s → 30s (max)
- On successful reconnect, resets to 1s
- The sidecar continues evaluating with the last known bundle during disconnection

## Data Model

Content is separated from metadata into immutable version records. Each update creates a new version snapshot.

```mermaid
erDiagram
    PolicySet {
        string id PK
        string domainId FK
        string name
        int latestVersion "auto-incremented on update"
        datetime createdAt
        datetime updatedAt
    }

    PolicySetVersion {
        string id PK
        string policySetId FK
        int version "immutable snapshot number"
        string content "Cedar policy text"
        string commitMessage "required"
        string createdBy "principal ID"
        datetime createdAt
    }

    AuthorizationSchema {
        string id PK
        string domainId FK
        string name
        int latestVersion
        datetime createdAt
        datetime updatedAt
    }

    AuthorizationSchemaVersion {
        string id PK
        string schemaId FK
        int version
        string content "Cedar schema JSON"
        string commitMessage
        string createdBy
        datetime createdAt
    }

    EntityStore {
        string id PK
        string domainId FK
        string name
        int latestVersion
        datetime createdAt
        datetime updatedAt
    }

    EntityStoreVersion {
        string id PK
        string entityStoreId FK
        int version
        string content "Cedar entities JSON"
        string commitMessage
        string createdBy
        datetime createdAt
    }

    AuthorizationBundle {
        string id PK
        string domainId FK
        string name
        string description
        string engineType
        string policySetId FK
        int policySetVersion "pinned version"
        boolean policySetPinToLatest "resolve latest at runtime"
        string schemaId FK
        int schemaVersion
        boolean schemaPinToLatest
        string entityStoreId FK
        int entityStoreVersion
        boolean entityStorePinToLatest
        datetime createdAt
        datetime updatedAt
    }

    AuthorizationEngine {
        string id PK
        string domain FK
        string name
        string type "plugin type"
        string configuration "JSON: apiKey + bundleId"
    }

    PolicySet ||--o{ PolicySetVersion : "has versions"
    AuthorizationSchema ||--o{ AuthorizationSchemaVersion : "has versions"
    EntityStore ||--o{ EntityStoreVersion : "has versions"
    AuthorizationBundle ||--o| PolicySet : "references"
    AuthorizationBundle ||--o| AuthorizationSchema : "references"
    AuthorizationBundle ||--o| EntityStore : "references"
    AuthorizationEngine ||--o| AuthorizationBundle : "binds via config.bundleId"
```

## Versioning Model

Each component (PolicySet, AuthorizationSchema, EntityStore) follows the same pattern:

- **Metadata table** stores only name + `latestVersion` counter
- **Version table** stores immutable snapshots: content, commitMessage, createdBy, createdAt
- On **create**: metadata record (latestVersion=1) + first version record
- On **update**: metadata latestVersion++ + new version record (with content or copied from previous)
- On **restore**: equivalent to update with content from an old version record
- On **delete**: cascade delete all version records, then metadata

### Version API Endpoints (per component)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/{id}/versions` | List all version records |
| GET | `/{id}/versions/{version}` | Get specific version (with content) |
| POST | `/{id}/versions/{version}/restore` | Create new version from old content |

### Pin-to-Latest (Bundles)

Bundles reference components with optional `pinToLatest` flags:

- **`pinToLatest=true`**: Gateway resolves content from the latest version at runtime (`findLatestByXxxId`)
- **`pinToLatest=false`**: Gateway resolves content from a specific pinned version (`findByXxxIdAndVersion`)

This allows bundles to either track the latest changes automatically or lock to a known-good version.

## Hot-Reload Flow (Bundle Update via WebSocket)

```mermaid
sequenceDiagram
    actor User
    participant UI as Management Console
    participant API as Management API
    participant SVC as PolicySetService
    participant DB as Repository
    participant BSVC as BundleService
    participant EVT as EventManager
    participant GW as AuthorizationEngineManager
    participant PROV as SidecarProvider
    participant SC as Sidecar (Cedar)

    User->>UI: Update policy set content + commit message
    UI->>API: PUT /authorization/policy-sets/{id}
    API->>SVC: policySetService.update(domain, id, request, principal)
    SVC->>DB: save metadata (latestVersion++)
    SVC->>DB: create PolicySetVersion record
    SVC->>SVC: audit report
    SVC-->>API: updated PolicySet

    User->>UI: Update bundle (select new policy set version)
    UI->>API: PUT /authorization/bundles/{id}
    API->>BSVC: bundleService.update()
    BSVC->>DB: save bundle
    BSVC->>EVT: emit AuthorizationBundleEvent(UPDATE)
    BSVC->>BSVC: audit report
    BSVC-->>API: updated Bundle

    EVT->>GW: onBundleEvent(UPDATE, bundleId)
    GW->>DB: findById(bundleId)
    DB-->>GW: Bundle{policySetId, pinToLatest, version}

    par Resolve content from version tables
        GW->>DB: policySetVersionRepo.findLatest/findByVersion
        DB-->>GW: PolicySetVersion.content
    and
        GW->>DB: schemaVersionRepo.findLatest/findByVersion
        DB-->>GW: AuthorizationSchemaVersion.content
    and
        GW->>DB: entityStoreVersionRepo.findLatest/findByVersion
        DB-->>GW: EntityStoreVersion.content
    end

    GW->>PROV: updateConfig(ResolvedBundleSnapshot)
    PROV->>PROV: cache lastBundleUpdate from snapshot

    Note over PROV,SC: Broadcast to all connected sidecars
    PROV->>SC: bundle_update {version, policy, data, schema}
    SC->>SC: Cedar engine reloaded (zero downtime)
```

## WebSocket Connection Flow

```mermaid
sequenceDiagram
    participant SC as Sidecar
    participant PROV as SidecarProvider (route on domain Router)

    SC->>PROV: WS UPGRADE /<domain-path>/_authz/ws (X-API-Key: secret)

    Note over PROV: Route registered via configureGatewayRoutes(router)
    PROV->>PROV: Extract API key from X-API-Key header (or apiKey query param)
    PROV->>PROV: Constant-time compare apiKey (MessageDigest.isEqual)

    alt API key valid
        PROV->>PROV: ctx.request().toWebSocket()
        PROV->>PROV: activeSessions.add(ws)
        PROV->>PROV: Register text/close/exception handlers
        PROV->>SC: Send current bundle (if available)
        Note over SC,PROV: Connection established
    else API key invalid
        PROV->>SC: 403 Forbidden
    end
```

## AuthZEN Evaluation Flow (with WebSocket Audit)

```mermaid
sequenceDiagram
    actor App as Client Application
    participant SC as Sidecar (Cedar)
    participant WS as GatewayWsClient
    participant GW as Gateway (Provider)
    participant AUD as AuditService

    App->>SC: POST /access/v1/evaluation
    Note right of App: {subject: {type: "User", id: "alice"},<br/>action: {name: "view"},<br/>resource: {type: "Document", id: "doc-123"}}

    SC->>SC: Cedar evaluate(policy, entities, schema, request)
    SC-->>App: {decision: true}

    SC->>WS: sendAuditEvent(decisionId, timestamp, decision, ...)
    WS->>GW: audit_event (WS message)
    Note right of WS: {decisionId, principalId, action,<br/>resourceType, resourceId, decision, engine: "cedar"}
    GW->>AUD: auditCallback.onEvaluation(event)
    AUD->>AUD: report(PermissionEvaluatedAuditBuilder)
```

## Version History Flow

```mermaid
sequenceDiagram
    actor User
    participant UI as Management Console
    participant API as Management API
    participant SVC as Service Layer
    participant DB as Repository

    User->>UI: Open Version History tab
    UI->>API: GET /authorization/policy-sets/{id}/versions
    API->>SVC: policySetService.getVersions(policySetId)
    SVC->>DB: policySetVersionRepo.findByPolicySetId(id)
    DB-->>SVC: List<PolicySetVersion>
    SVC-->>API: versions
    API-->>UI: version list (version, commitMessage, createdBy, createdAt)

    User->>UI: View version N
    UI->>API: GET /authorization/policy-sets/{id}/versions/{N}
    API->>SVC: policySetService.getVersion(policySetId, N)
    SVC->>DB: policySetVersionRepo.findByPolicySetIdAndVersion(id, N)
    DB-->>SVC: PolicySetVersion
    SVC-->>API: version with content
    API-->>UI: show read-only content

    User->>UI: Click "Restore"
    UI->>API: POST /authorization/policy-sets/{id}/versions/{N}/restore
    API->>SVC: policySetService.restoreVersion(domain, id, N, principal)
    SVC->>DB: find old version content
    SVC->>DB: update metadata (latestVersion++)
    SVC->>DB: create new version record ("Restore to version N")
    SVC->>SVC: audit report
    SVC-->>API: updated PolicySet
    API-->>UI: refreshed data
```

## Sidecar Deployment

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GATEWAY_URL` | Yes | AM Gateway base URL (e.g., `http://gateway:8092`) |
| `SIDECAR_DOMAIN_PATH` | Yes | Security domain path (e.g., `mydom`) |
| `SIDECAR_API_KEY` | Yes | API key (must match gateway engine configuration) |
| `SIDECAR_PORT` | No | HTTP port for evaluation endpoint (default: 8081) |

### Docker Example

```yaml
services:
  cedar-sidecar:
    image: gravitee/am-sidecar-cedar:latest
    environment:
      GATEWAY_URL: http://gateway:8092
      SIDECAR_DOMAIN_PATH: mydom
      SIDECAR_API_KEY: ${SIDECAR_API_KEY}
      SIDECAR_PORT: 8081
    ports:
      - "8081:8081"
```

### Key Properties

- **Self-healing**: Automatic reconnect with exponential backoff on disconnect
- **Offline resilience**: Continues evaluating with last known bundle if gateway is unreachable
- **Zero-config updates**: Bundle changes pushed automatically via WebSocket
- **Stateless sidecar**: No local storage needed, all state received from gateway

## Component Inventory

### Gateway Side

| Component | File | Role |
|-----------|------|------|
| Route Registration | `AuthorizationEngineManagerImpl` | Calls `provider.configureGatewayRoutes(router)` on deploy |
| WS Route + Auth + Sessions | `SidecarAuthorizationEngineProvider` | Registers `/_authz/ws` route, API key validation, session management, bundle broadcasting |
| Health Resource | `SidecarManagementResource` | `GET /health` — connected sidecars, bundle version |

### Sidecar Side

| Component | File | Role |
|-----------|------|------|
| WS Client | `GatewayWsClient` | Connects to gateway, periodic bundle check, audit send |
| Engine | `CedarEngineManager` | Cedar policy deploy/evaluate with read-write lock |
| Evaluation | `AuthZenEvaluationHandler` | `POST /access/v1/evaluation` — AuthZEN endpoint |
| Health | `HealthHandler` | `GET /health` — engine status + metrics |

### WS Protocol

| Component | File | Role |
|-----------|------|------|
| Message Types | `WsMessage.java` | Sealed interface with 5 record types |
| Codec | `WsMessageCodec.java` | JSON encode/decode with `type` discriminator |
| Bundle Snapshot | `ResolvedBundleSnapshot.java` | Immutable record passed from manager to provider on bundle resolve |

## Audit Logging

All C/U/D operations emit audit events via dedicated AuditBuilders:

| Entity | AuditBuilder | Event Types |
|--------|-------------|-------------|
| PolicySet | `PolicySetAuditBuilder` | CREATED / UPDATED / DELETED |
| AuthorizationSchema | `AuthorizationSchemaAuditBuilder` | CREATED / UPDATED / DELETED |
| EntityStore | `EntityStoreAuditBuilder` | CREATED / UPDATED / DELETED |
| AuthorizationBundle | `AuthorizationBundleAuditBuilder` | CREATED / UPDATED / DELETED |
| Permission Evaluation | `PermissionEvaluatedAuditBuilder` | PERMISSION_EVALUATED |

Audit logging is implemented in the **Service layer** (not Resources). Resources pass the authenticated `User principal` to service methods.

Permission evaluation audits flow from the sidecar through the WebSocket connection to the gateway's audit pipeline.

## Health Endpoints

Both the gateway and sidecar expose health endpoints for monitoring.

### Gateway Side

`SidecarManagementResource` exposes:

```
GET /_authz/ws/health
```

Response:
```json
{
  "status": "UP",
  "connectedSidecars": 2,
  "bundleVersion": 5
}
```

### Sidecar Side

`HealthHandler` exposes:

```
GET /health
```

Response:
```json
{
  "status": "UP",
  "ready": true,
  "engine": {
    "policyLoaded": true,
    "entityCount": 42,
    "metrics": { "evaluations": 1234, "denials": 56 }
  }
}
```

## Initial Bundle Push on Connection

When a sidecar connects via WebSocket, the provider immediately sends the current bundle if one is cached. This ensures the sidecar does not have to wait for the next poll interval to receive its initial configuration.

```mermaid
sequenceDiagram
    participant SC as Sidecar
    participant PROV as SidecarProvider

    SC->>PROV: WS UPGRADE /_authz/ws (X-API-Key)
    PROV->>PROV: Validate API key
    PROV-->>SC: 101 Switching Protocols
    PROV->>PROV: Check lastBundleUpdate cache
    alt Bundle cached
        PROV-->>SC: bundle_update {version, policy, data, schema}
        SC->>SC: Deploy to Cedar engine
    else No bundle yet
        Note over SC,PROV: Sidecar waits for first bundle_update
    end
```

## Audit Pipeline Integration

Sidecars report evaluation decisions back to the gateway via WebSocket `audit_event` messages. The gateway integrates these into the standard AM audit pipeline.

```mermaid
sequenceDiagram
    participant SC as Sidecar
    participant PROV as SidecarProvider
    participant MGR as AuthorizationEngineManager
    participant AUD as AuditService

    SC->>PROV: audit_event {decisionId, principalId, action, resourceType, resourceId, decision, engine}
    PROV->>PROV: auditCallback.onEvaluation(event)
    PROV->>MGR: reportAuditEvent(AuthorizationAuditEvent)
    MGR->>MGR: Build AuthorizationEngineRequest + Response from event
    MGR->>AUD: report(PermissionEvaluatedAuditBuilder)
    AUD->>AUD: Persist audit record
```

The flow:
1. Sidecar sends `audit_event` WS message after each authorization evaluation
2. `SidecarAuthorizationEngineProvider` receives the message and calls the registered `AuthorizationAuditCallback`
3. `AuthorizationEngineManagerImpl.reportAuditEvent()` converts the provider-agnostic `AuthorizationAuditEvent` into a `PermissionEvaluatedAuditBuilder`
4. The builder is passed to `AuditService.report()` which persists it through the standard audit pipeline

## Architecture Comparison: HTTP Push vs WebSocket

| Aspect | HTTP Push (old) | WebSocket (current) |
|--------|----------------|---------------------|
| Connection direction | Gateway → Sidecar | Sidecar → Gateway |
| Config delivery | `PUT /config` on sidecar | `bundle_update` WS message |
| Audit delivery | HTTP callback from sidecar | `audit_event` WS message |
| Sidecar URL needed | Yes (configured in engine) | No |
| Reconnection | Manual/none | Automatic with exponential backoff |
| Real-time updates | Requires gateway to push | Gateway broadcasts to active sessions |
| Multiple sidecars | Each needs separate URL config | All connect to same WS endpoint |
| Firewall friendly | Requires sidecar port open | Sidecar initiates outbound connection |
