# Phase 11: AAUTH Bootstrap (PS-side)

## Goal

Implement the Person Server (PS) side of the AAUTH Bootstrap extension (`draft-hardt-aauth-bootstrap`) in Gravitee AM. Bootstrap is the ceremony by which a SaaS agent establishes its initial `aauth:local@domain` identity through the user's PS. The PS authenticates the user, collects consent, issues a `bootstrap_token` (a short-lived JWT directed at the Agent Server), and records the resulting agent binding after the Agent Server announces completion.

This phase implements:
- `POST /aauth/bootstrap` — initial request (signed `hwk`): accepts `agent_server` parameter, triggers deferred interaction, issues `bootstrap_token`
- `POST /aauth/bootstrap` — completion announcement (signed `jwt` with `aa-agent+jwt`, empty body): records the `(user, agent_identifier)` binding
- `GET /aauth/bootstrap/pending/:id` — polling endpoint for bootstrap deferred flow
- Bootstrap consent screen — shows the Agent Server's name/logo, asks the user to approve the binding
- `bootstrap_token` minting — JWT with `typ=aa-bootstrap+jwt`, `iss=PS`, `aud=Agent Server`, `sub=pairwise user id`, `cnf.jwk=agent's ephemeral key`

This phase does NOT implement the Agent Server side (that lives in the demo project), platform attestation (WebAuthn, App Attest), or the refresh endpoint. It focuses purely on what Gravitee AM (the PS) must do.

## Discovery

**Specification references:**
- [draft-hardt-aauth-bootstrap](../specs/draft-hardt-aauth-bootstrap.md) — Full bootstrap spec
- Section 6.2 — Request to PS /bootstrap: `hwk` signature, `agent_server` body parameter, optional `domain_hint`, `login_hint`, `tenant`
- Section 6.3 — Interaction Response: `202 Accepted`, `Location` header with pending URL, `AAuth-Requirement: requirement=interaction`
- Section 6.4 — bootstrap_token Issuance: JWT claims (`iss`, `dwk`, `aud`, `sub`, `cnf`, `jti`, `iat`, `exp`), `typ=aa-bootstrap+jwt`
- Section 6.7 — Bootstrap Completion: announcement POST with `jwt` scheme (empty body), PS records binding
- Section 3 — Terminology: `bootstrap_token`, binding, ephemeral key
- Section 5 — Agent Server Metadata Extensions: `bootstrap_endpoint`, `refresh_endpoint`, `webauthn_endpoint` in `aauth-agent.json`

**Existing code to reuse:**
- The 202 deferred interaction pattern from Phase 8 (pending request, polling, consent, interaction URL)
- `AAuthSignatureHandler` + `AAuthSignatureVerifier` for `hwk` signature verification on the initial request
- `JWTScheme` for `jwt` signature verification on the completion announcement
- `AAuthPendingRequestService` pattern for the bootstrap pending flow (or a dedicated bootstrap pending entity)
- `CertificateManager` + `JWTBuilder` for signing the `bootstrap_token`
- `AgentMetadataFetcher` to fetch the Agent Server's metadata for the consent screen (name, logo)
- Thymeleaf template engine for the bootstrap consent page

### Key differences from the authorization deferred flow

| Aspect | Authorization (Phase 8) | Bootstrap (this phase) |
|--------|------------------------|----------------------|
| Trigger | `POST /aauth/token` with `resource_token` | `POST /aauth/bootstrap` with `agent_server` |
| Signature scheme | `jwt` (aa-agent+jwt required) | `hwk` (ephemeral key, no agent identity yet) |
| Consent screen | Shows scopes, justification, clarification | Shows Agent Server name/logo, no scopes |
| Result | `auth_token` (aa-auth+jwt) | `bootstrap_token` (aa-bootstrap+jwt) |
| Completion | Agent polls, gets auth_token, done | Agent polls, gets bootstrap_token, then announces back to PS |
| User identifier | Existing user (already authenticated) | Pairwise directed identifier (`sub`) for the Agent Server |

### bootstrap_token vs auth_token

The `bootstrap_token` is NOT an `auth_token`. It:
- Has `typ=aa-bootstrap+jwt` (not `aa-auth+jwt`)
- Is directed at the Agent Server (`aud`), not at a resource
- Contains a pairwise `sub` directed at `aud` (not the user's internal ID)
- Contains `cnf.jwk` binding the agent's ephemeral public key
- Does NOT contain `scope`, `agent`, or user attributes
- Has a very short lifetime (5 minutes max per spec)
- Is consumed by the Agent Server, not by a resource

## Design

### Bootstrap Flow (PS perspective)

```mermaid
sequenceDiagram
    participant CLI as Agent CLI
    participant PS as Gravitee AM (PS)
    participant Browser as User Browser
    participant AS as Agent Server

    Note over CLI: Generate ephemeral<br/>Ed25519 keypair

    CLI->>PS: POST /aauth/bootstrap<br/>body { agent_server: 'https://agent-server.example' }<br/>Signature-Key sig=hwk (ephemeral key)

    Note over PS: Verify hwk signature<br/>Fetch Agent Server metadata<br/>Create bootstrap pending request

    PS-->>CLI: 202 Accepted<br/>Location /aauth/bootstrap/pending/{id}<br/>AAuth-Requirement requirement=interaction,<br/>url=.../aauth/bootstrap-interact, code=ABCD-1234

    CLI->>Browser: Print interaction URL
    Browser->>PS: GET /aauth/bootstrap-interact?code=ABCD-1234
    PS->>Browser: Login page
    Browser->>PS: Authenticate as alice
    PS->>Browser: 302 redirect to /aauth/bootstrap-consent?code=ABCD-1234
    Browser->>PS: GET /aauth/bootstrap-consent?code=ABCD-1234
    PS->>Browser: Bootstrap consent screen<br/>'Agent Server agent-server.example<br/>wants to create an agent identity'
    Browser->>PS: POST /aauth/bootstrap-consent<br/>code=ABCD-1234, user_oauth_approval=true

    Note over PS: Mint bootstrap_token JWT<br/>typ=aa-bootstrap+jwt<br/>iss=PS, aud=AS<br/>sub=pairwise(alice, AS)<br/>cnf.jwk=ephemeral key

    CLI->>PS: GET /aauth/bootstrap/pending/{id}<br/>signed hwk
    PS-->>CLI: 200 OK<br/>body { bootstrap_token: 'eyJ...' }

    Note over CLI: CLI sends bootstrap_token<br/>to Agent Server (out of scope)
    Note over CLI: Agent Server returns<br/>aa-agent+jwt (out of scope)

    CLI->>PS: POST /aauth/bootstrap (empty body)<br/>Signature-Key sig=jwt, jwt=aa-agent+jwt
    
    Note over PS: Verify jwt signature<br/>Verify agent_token.ps == this PS<br/>Look up bootstrap record by cnf.jwk thumbprint<br/>Record binding: agent_token.sub ↔ (user, agent_server)

    PS-->>CLI: 204 No Content
```

### Pending Request Lifecycle

Bootstrap reuses the same deferred interaction pattern as authorization but with a dedicated entity (`AAuthBootstrapRequest`) to avoid overloading the existing `AAuthPendingRequest` which carries authorization-specific fields (scope, resource_iss, auth_token, clarification, etc.).

**Decision: separate entity, not a column-extended `AAuthPendingRequest`.** The bootstrap and authorization flows have distinct consent UIs, distinct state machines, and distinct correlation keys (ephemeral-key thumbprint vs. resource_token). A merged entity would make the schema harder to reason about and would force every authorization-path query to filter on `flow_type`. The minor cost is duplicating the polling endpoint and the TTL purge job, both of which are small.

```mermaid
stateDiagram-v2
    [*] --> PENDING: POST /bootstrap returns 202
    PENDING --> INTERACTING: User opens interaction URL
    INTERACTING --> COMPLETED: User approves → bootstrap_token minted
    PENDING --> DENIED: User denies
    PENDING --> EXPIRED: TTL exceeded
    INTERACTING --> DENIED: User denies
    INTERACTING --> EXPIRED: TTL exceeded
    COMPLETED --> EXPIRED: TTL exceeded
    COMPLETED --> [*]: Record purged after expireAt
    DENIED --> [*]
    EXPIRED --> [*]
```

**Lifecycle invariants** (per spec §6.7):
- The record is retained at least until `expireAt`, regardless of state. `COMPLETED` does **not** mean "done" — the announcement still needs to correlate by ephemeral thumbprint until the TTL purge runs.
- The poll endpoint is idempotent: once a request is `COMPLETED`, subsequent polls keep returning the same `bootstrap_token` until expiry. The status does not transition further on poll.
- The announcement endpoint is idempotent: it writes (or refreshes) the `AAuthBootstrapBinding` row but does **not** transition the request status. Repeated announcements for the same ephemeral thumbprint return `204 No Content` (see Completion announcement processing below).
- TTL-based purge runs on `expireAt`, not on state.

**Why we don't model `BOOTSTRAP_TOKEN_DELIVERED` / `ANNOUNCED` as separate states.** Both milestones can be derived from existing data: "delivered" = the agent has successfully fetched a non-null `bootstrapToken` (visible via `lastAccessAt` and the token field); "announced" = an `AAuthBootstrapBinding` row exists for `(userId, agentServerUrl)`. Adding enum values would tighten the wire model without changing observable behaviour, so the implementation keeps the simpler `PENDING / INTERACTING / COMPLETED / DENIED / EXPIRED` set already shared with the authorization flow's `PendingRequestStatus`.

### Pairwise User Identifier

The `bootstrap_token.sub` MUST be a pairwise identifier directed at the Agent Server. This prevents cross-vendor user correlation. The identifier should be:
- Deterministic: same `(user, identity, agent_server)` always produces the same `sub`
- Opaque: the Agent Server cannot derive the user's internal ID from it
- Directed: different Agent Servers get different `sub` values for the same user
- Identity-scoped: when the user has multiple identities at this PS (B2B), each identity gets its own pairwise `sub` per agent server

Implementation: `SHA-256(user_internal_id | identity_id | agent_server_url | domain_secret)`, base64url-encoded, truncated to 32 chars (192 bits). See `PairwiseSubjectGenerator` below for the exact form, including the `identityId` parameter and the `domain_secret` sourcing rules.

### Bootstrap Binding Storage

After the Agent Server announces completion (Step 8), the PS records the binding:
- `userId` — the AM user who approved
- `agentServerUrl` — the Agent Server URL from the initial request
- `agentIdentifier` — the `aauth:local@domain` from `agent_token.sub`
- `ephemeralKeyThumbprint` — JWK thumbprint of the ephemeral key (used to correlate the announcement)
- `createdAt` — binding creation timestamp
- `updatedAt` — last touch (re-bootstrap, lazy learn from resource_token)

This is stored in a new `aauth_bootstrap_bindings` collection/table.

**Uniqueness**: a unique index on `(domain, userId, agentServerUrl)`. Per spec §6.6, the AS-side binding `(ps_url, user_sub) → aauth:local@<agent-server-domain>` is one-to-one; the PS-side mirror MUST be one-to-one on `(userId, agentServerUrl)`. Re-bootstrapping the same pair MUST be an upsert, not a duplicate insert.

**Conflict handling**: if a binding already exists for `(userId, agentServerUrl)` and the announcement carries a *different* `agentIdentifier` than the stored one, that is a protocol violation (the AS gave us a different `aauth:local@domain` for the same pair). The PS MUST refuse with `409 Conflict` and log the divergence — do NOT silently overwrite, that would mask a misbehaving AS.

## Implementation

### Data Model

**New entity: `AAuthBootstrapRequest`** — similar to `AAuthPendingRequest` but for bootstrap:

```java
public class AAuthBootstrapRequest {
    private String id;
    private String status;                  // PENDING, INTERACTING, COMPLETED, DENIED, EXPIRED  (shared with authorization flow's PendingRequestStatus)
    private String domain;
    private String agentServerUrl;          // from request body "agent_server"
    private String agentServerName;         // fetched from Agent Server metadata
    private String agentServerLogoUri;      // fetched from Agent Server metadata
    private String ephemeralKeyJwk;         // serialized JWK of the agent's ephemeral public key
    private String ephemeralKeyThumbprint;  // JWK thumbprint for correlating announcement
    private String interactionCode;         // human-readable code (XXXX-NNNN)
    private String bootstrapToken;          // the minted bootstrap_token JWT (set when status moves to COMPLETED)
    private String userId;                  // set when user authenticates
    private String pairwiseSub;             // the directed sub for this (user, agent_server)
    private String domainHint;              // optional, from request
    private String loginHint;               // optional, from request
    private String tenant;                  // optional, from request
    private Date createdAt;
    private Date lastAccessAt;
    private Date expireAt;
}
```

**New entity: `AAuthBootstrapBinding`** — records the user-agent binding after completion:

```java
public class AAuthBootstrapBinding {
    private String id;
    private String domain;
    private String userId;
    private String agentServerUrl;
    private String agentIdentifier;         // aauth:local@domain from agent_token.sub
    private String pairwiseSub;             // the directed sub issued to this agent server
    private Date createdAt;
    private Date updatedAt;
}
```

### Files to Create

```
aauth/
  model/
    AAuthBootstrapRequest.java           -- Bootstrap pending request entity
    AAuthBootstrapBinding.java           -- User-agent binding entity
  resources/endpoint/
    AAuthBootstrapEndpoint.java          -- POST /aauth/bootstrap (both initial + announcement)
    AAuthBootstrapPendingEndpoint.java   -- GET /aauth/bootstrap/pending/:id (polling)
  resources/handler/
    AAuthBootstrapConsentHandler.java    -- GET /aauth/bootstrap/consent (renders consent page)
    AAuthBootstrapConsentPostEndpoint.java -- POST /aauth/bootstrap/consent (approve/deny)
    AAuthBootstrapInteractHandler.java   -- GET /aauth/interact for bootstrap codes (may reuse existing)
  service/
    AAuthBootstrapService.java           -- Business logic: create, poll, approve, deny, announce
    PairwiseSubjectGenerator.java        -- Generates pairwise sub for (user, agent_server)
    BootstrapTokenMinter.java            -- Mints aa-bootstrap+jwt
```

### Files to Modify

```
aauth/
  AAuthProvider.java                     -- Register bootstrap routes
  spring/AAuthConfiguration.java         -- Wire bootstrap beans
  resources/handler/AAuthInteractionResolveHandler.java  -- Handle bootstrap interaction codes
```

### Templates

```
webroot/views/
  aauth_bootstrap_consent.html           -- Bootstrap consent screen (Agent Server name/logo, approve/deny)
```

### Repository (MongoDB + JDBC + Liquibase)

```
repository-api/
  AAuthBootstrapRequestRepository.java   -- CRUD for bootstrap requests
  AAuthBootstrapBindingRepository.java   -- CRUD for bindings

repository-mongodb/
  MongoAAuthBootstrapRequestRepository.java
  AAuthBootstrapRequestMongo.java
  MongoAAuthBootstrapBindingRepository.java
  AAuthBootstrapBindingMongo.java

repository-jdbc/
  JdbcAAuthBootstrapRequestRepository.java
  JdbcAAuthBootstrapRequest.java
  JdbcAAuthBootstrapBindingRepository.java
  JdbcAAuthBootstrapBinding.java

liquibase/
  4.12.0-aauth-bootstrap-requests.yml
  4.12.0-aauth-bootstrap-bindings.yml
```

### Key Implementation Details

**`AAuthBootstrapEndpoint` (POST /aauth/bootstrap)**

This endpoint handles TWO different requests distinguished by signature scheme and body:
1. **Initial request** (`hwk` scheme, JSON body with `agent_server`): creates a bootstrap pending request, returns 202
2. **Completion announcement** (`jwt` scheme with `aa-agent+jwt`, empty body): records the binding, returns 204

```java
public void handle(RoutingContext ctx) {
    VerificationResult verification = ctx.get("aauth.verification");

    if ("jwt".equals(verification.scheme())) {
        // Completion announcement: empty body, jwt signature with agent_token
        handleAnnouncement(ctx, verification);
    } else if ("hwk".equals(verification.scheme())) {
        // Initial bootstrap request: JSON body with agent_server
        handleInitialRequest(ctx, verification);
    } else {
        ctx.fail(new InvalidRequestException("Unsupported signature scheme for bootstrap"));
    }
}
```

**Initial-request validation** (per spec §5 + §6.2). Before creating the pending request, `handleInitialRequest` MUST:

1. Parse the body as JSON; require `agent_server` (string).
2. Validate `agent_server`: parses as a URL, scheme is `https`, no query, no fragment, no userinfo. Reject `400 invalid_request` otherwise.
3. Fetch the AS metadata at `agent_server + "/.well-known/aauth-agent.json"` (see "AS metadata fetch" below).
4. Validate `metadata.issuer == agent_server` (origin-equal). Reject `400 invalid_request` if mismatched — this defends against typo-squat agent servers and matches the agent-side requirement in spec §5.
5. Compute `ephemeralKeyThumbprint` from the `Signature-Key` `hwk` (RFC 7638). Persist on the pending record so the announcement can correlate.

**`BootstrapTokenMinter`**

Mints `aa-bootstrap+jwt` using the PS's signing certificate (same as auth_token signing):
```java
JWT bootstrapToken = new JWT();
bootstrapToken.setIss(psIssuerUrl);
bootstrapToken.set("dwk", "aauth-person.json");
bootstrapToken.setAud(agentServerUrl);
bootstrapToken.setSub(pairwiseSub);
bootstrapToken.set("cnf", Map.of("jwk", ephemeralKeyJwk));
bootstrapToken.setJti(UUID.randomUUID().toString());
bootstrapToken.setIat(now);
bootstrapToken.setExp(now + 300);  // 5 minutes

// Sign with typ=aa-bootstrap+jwt
return jwtBuilder.sign(bootstrapToken, "aa-bootstrap+jwt");
```

**`PairwiseSubjectGenerator`**

Generates a deterministic pairwise `sub` for `(user, identity, agent_server)`:
```java
private static final int PAIRWISE_SUB_BYTES = 24;  // 192-bit; 32-char base64url

public String generate(String userId, String identityId, String agentServerUrl, String domainSecret) {
    String key = userId + "|" + (identityId == null ? "" : identityId) + "|" + agentServerUrl + "|" + domainSecret;
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(UTF_8));
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(Arrays.copyOf(hash, PAIRWISE_SUB_BYTES));
}
```

- `identityId` is the AM identity selected via B2B parameters (`domain_hint`, `tenant`). When the user has only a primary identity, pass `null` (or the primary identity's id — pick one and stay consistent across calls so the pairwise stays stable).
- `domainSecret` MUST come from the AM security domain configuration, not from any user-scoped material. Otherwise pairwise becomes user-derivable, defeating the unlinkability property.
- `PAIRWISE_SUB_BYTES = 24` is a chosen constant (192 bits is well above any collision concern at realistic scale). The spec does not mandate a length.

**Bootstrap consent screen (`aauth_bootstrap_consent.html`)**

Simpler than the authorization consent screen — no scopes, no clarification:
```html
<div class="header-description">
    <img th:if="${agentServerLogoUri != null}" th:src="${agentServerLogoUri}" ...>
    <span th:text="${agentServerName}"></span>
    <span> wants to create an agent identity for you</span>
</div>
<div class="section">
    <button name="user_approval" value="true">Approve</button>
    <button name="user_approval" value="false">Deny</button>
</div>
```

**Completion announcement processing** (per spec §6.7)

When the agent POSTs back with `jwt` scheme and empty body:

1. Verify HTTP Message Signature under `jwt` scheme.
2. Extract `agent_token` from `Signature-Key` header.
3. Verify `agent_token` by fetching the Agent Server's JWKS via `iss` + `dwk` (reuse Phase 9's resolver — see "JWKS reuse" below).
4. Check `agent_token.ps == this PS URL`. Reject `400` if mismatched.
5. Compute thumbprint of `agent_token.cnf.jwk`.
6. Look up the `AAuthBootstrapRequest` by that thumbprint, scoped to this domain.
   - **Not found** → respond `404 Not Found` (the announcement may be late; record may have been purged after `expireAt`, or the thumbprint never matched).
7. Look up an existing `AAuthBootstrapBinding` by `(userId, agentServerUrl)` from the request.
   - **Exists with same `agentIdentifier`** → idempotent path: touch `updatedAt` on the binding, respond `204 No Content`. The request's `status` stays at `COMPLETED`.
   - **Exists with different `agentIdentifier`** → respond `409 Conflict`, log the divergence. Do not overwrite.
   - **Does not exist** → upsert the binding with `agentIdentifier = agent_token.sub`, respond `204 No Content`. The request's `status` stays at `COMPLETED` (the binding row is the durable record of the announcement; the request itself is purged on TTL).

Idempotency is mandatory per spec §6.7: repeated announcements for the same ephemeral thumbprint after a successful binding "have no effect and respond `204 No Content`."

**AS metadata fetch (graceful degradation)**

The consent screen needs the AS's `client_name` and `logo_uri` from `/.well-known/aauth-agent.json` (spec §6.3). Behavior when the metadata is unavailable or partial:

- **Reachable, complete**: render `client_name` + `logo_uri` as-is.
- **Reachable, missing `client_name`**: fall back to the `agent_server` hostname.
- **Reachable, missing `logo_uri`**: render no logo (no generic placeholder that could be mistaken for an unverified identity).
- **Unreachable** (timeout 3 s, bounded retries): refuse to mint a `bootstrap_token` and return `400 invalid_request` with `error_description="agent_server_metadata_unreachable"`. Fail closed: showing a consent screen with no verifiable AS identity invites consent phishing (spec §11.2).
- **Reachable, `issuer` mismatch**: reject `400 invalid_request` (typo-squat defense, see "Initial-request validation").

This is a stricter posture than spec language alone requires — spec uses *SHOULD* for the consent display — but consent without identifying information is the precise scenario §11.2 warns about.

**JWKS reuse**

Both the announcement verification (resolves the AS's JWKS via `agent_token.iss + dwk`) and any future agent_token verification at the PS MUST go through the JWKS resolver introduced in Phase 9. No new caching layer is added here. Cache TTL, unknown-`kid` triggered refetch, and rate limiting all live in that resolver.

**B2B parameters**

`domain_hint`, `login_hint`, and `tenant` (spec §10) are accepted on the initial request and persisted on `AAuthBootstrapRequest`. Their effect:

- `domain_hint` / `login_hint` — pre-select an identity in the user-authentication step. If the user has multiple identities at this PS, these hints SHOULD pick one without prompting; if they do not match any identity, the PS prompts the user to choose.
- The selected identity's id is fed into `PairwiseSubjectGenerator.generate(...)` as `identityId`. This guarantees that two identities of the same user at the same AS get different pairwise `sub` values (and hence different `aauth:local@domain` agents at the AS).
- `tenant` — recorded on the resulting `AAuthBootstrapBinding` so subsequent three-party `auth_token` requests resolve against the same organizational context. Phase 14 (Missions) and Phase 6 (PS Token Endpoint) consume this.

For this phase, the parameters are accepted, validated, and stored. Full B2B identity-selection UX (multi-identity picker, tenant-routing in the token endpoint) is out of scope and belongs to a future B2B phase.

**Future integrations** (deferred — non-blocking)

- *Lazy binding learning* (spec §6.7, MAY): Phase 6's PS Token Endpoint, when it sees a `resource_token.agent` whose `(userId, agentServerUrl)` has no `AAuthBootstrapBinding` yet, MAY upsert one with the observed `agent_identifier`. This recovers the binding when an announcement was missed (e.g., the bootstrap record had already been purged) and is also the path by which non-bootstrap-onboarded agents (self-hosted, CLI, workload-attested) become known to the PS. To be added under a domain-level setting once Phase 6 is updated; not required for this phase to ship.

- *Revocation — data model* (Phase 11 follow-up, near-term): extend `AAuthBootstrapBinding` with `revokedAt` (nullable timestamp) and `revokedBy` (who revoked: `user`, `admin`, `system`). Add a service method `revokeBinding(bindingId, actor)` that sets these fields. Without this, "revoke" has nothing to write to.

- *Revocation — enforcement at the PS Token Endpoint* (Phase 6 follow-up, near-term): when the agent presents a `resource_token` whose `agent` claim matches a binding with `revokedAt != null`, refuse to issue an `auth_token` and respond with the `aauth_revoked` error. This is what makes revocation actually bite — it stops the agent from completing PS-mediated three-party flows. The agent's own `agent_token` may continue to work for identity-only calls until it expires; revocation enforcement at the PS is best-effort against the AS, which the AS must complement (see next bullet).

- *Revocation — downstream notification via OPC* (deferred, optional): once revoked at the PS, push the revocation to the AS via OpenID Provider Commands so the AS can stop renewing `agent_token`s for that binding. AAuth Protocol spec lists OPC as the complementary lifecycle protocol. Best-effort: not every AS implements OPC. Without OPC, agent_token renewal continues to succeed at the AS until the user revokes the device credential there separately.

- *User-facing "connected agents" page* (spec §11.4, SHOULD): an end-user account-management view listing bindings, with per-binding revoke action that calls into the revocation service above. Belongs to Phase 5 (Management UI) or a follow-up; out of scope here, but note that the revocation data-model and enforcement work above are prerequisites.

### Route Registration

```java
// In AAuthProvider.java
// Bootstrap routes
router.post("/aauth/bootstrap")
    .handler(signatureHandler)           // verifies hwk or jwt
    .handler(bootstrapEndpoint);         // dispatches based on scheme

router.get("/aauth/bootstrap/pending/:id")
    .handler(signatureHandler)           // verifies hwk
    .handler(bootstrapPendingEndpoint);  // polling

// Bootstrap interaction (reuse existing interact pattern)
router.get("/aauth/bootstrap/consent")
    .handler(authenticationFlowHandler)  // ensures user is logged in
    .handler(bootstrapConsentHandler);   // renders consent page

router.post("/aauth/bootstrap/consent")
    .handler(csrfHandler)
    .handler(bootstrapConsentPostEndpoint);  // approve/deny
```

### PS Metadata Extension

Add `bootstrap_endpoint` to the PS metadata at `/.well-known/aauth-person.json`:

```json
{
  "issuer": "https://am.example/aauth-demo/aauth",
  "jwks_uri": "https://am.example/aauth-demo/aauth/.well-known/jwks.json",
  "token_endpoint": "https://am.example/aauth-demo/aauth/token",
  "bootstrap_endpoint": "https://am.example/aauth-demo/aauth/bootstrap"
}
```

## Validation

### Unit tests
- `PairwiseSubjectGeneratorTest` — deterministic; differs across agent servers; differs across identities at the same agent server (B2B); opaque (no userId recoverable from sub).
- `BootstrapTokenMinterTest` — correct claims (`iss`, `dwk`, `aud`, `sub`, `cnf`, `jti`, `iat`, `exp`); `typ=aa-bootstrap+jwt`; `exp - iat ≤ 300`; signed by PS key.
- `AAuthBootstrapServiceTest` — full state-machine traversal: PENDING → INTERACTING → COMPLETED; plus DENIED and EXPIRED branches; plus the announce side-effect (binding row created/refreshed/conflicted) which lives outside the request's status field.
- `AAuthBootstrapEndpointTest` — initial-request (hwk) vs announcement (jwt) dispatch; rejection of unsupported schemes.
- `AgentServerUrlValidatorTest` — accept `https://...`, reject `http://...`, reject URLs with query/fragment/userinfo, reject malformed URLs.

### Integration tests
- **Happy path**: full bootstrap ceremony with a mock Agent Server: hwk request → 202 → user logs in → consent approve → poll → `bootstrap_token` → announcement → 204; verify binding row written.
- **Idempotent announcement**: repeat the announcement with the same ephemeral thumbprint → 204; binding row unchanged (no duplicate, `updatedAt` may bump).
- **Conflicting announcement**: announce with a different `agent_token.sub` for an existing `(userId, agentServerUrl)` binding → 409; no overwrite.
- **Late announcement after purge**: simulate `expireAt` purge of the bootstrap record, then announce → 404.
- **Unknown thumbprint**: announce with an ephemeral thumbprint that never had a bootstrap record → 404.
- **User denial**: user denies on consent → agent polls → DENIED status surfaced.
- **Expiry pre-issuance**: TTL exceeded before user approves → EXPIRED; agent polling reflects it.
- **URL validation**: `agent_server=http://...` → 400; `agent_server=https://...?x=y` → 400; `agent_server=https://user:pass@...` → 400.
- **AS metadata unreachable**: mock AS metadata endpoint returns 5xx → initial request rejected with `400 invalid_request` (`agent_server_metadata_unreachable`).
- **AS metadata `issuer` mismatch**: AS metadata returns `issuer="https://other.example"` but request had `agent_server="https://agent-server.example"` → 400.
- **Pairwise sub stability across re-bootstrap**: bootstrap, then re-bootstrap same `(user, agent_server)` → same `bootstrap_token.sub`; binding upserted not duplicated.
- **B2B `domain_hint`**: bootstrap with `domain_hint` selecting a non-default identity → pairwise `sub` differs from the one produced without the hint.
- **PS metadata exposure**: GET `/.well-known/aauth-person.json` returns `bootstrap_endpoint`.

### Manual test
- Use `AAuthTokenEndpointManualTest` pattern: extend with bootstrap ceremony.
- Verify `bootstrap_token` JWT structure and claims, including `cnf.jwk` matches the `hwk` from the initial request.
- Verify pairwise `sub` is consistent across requests for the same `(user, identity, agent_server)`.
- Verify binding is recorded with the expected `agent_identifier` after the announcement, and that a second announcement returns 204 without writing a duplicate row.

## Dependencies

| Phase | Why needed |
|-------|-----------|
| P1 | PS metadata at `/.well-known/aauth-person.json` — add `bootstrap_endpoint` |
| P2 | HTTP Message Signature verification (`hwk` scheme) |
| P8 | Deferred interaction pattern (202, pending URL, consent UI) — architectural pattern reuse |
| P9 | JWT signature scheme verification (for the completion announcement with `aa-agent+jwt`) |
