# Phase 16: End-to-End Tests

## Goal

Provide complete spec-coverage end-to-end tests for the AAUTH protocol implementation, running against a fully-deployed Gravitee Access Management instance. Where Phases 1-14 each ship Java unit tests that mock dependencies, Phase 16 runs the **entire stack** (gateway + management API + persistence + new AAUTH plugin) and exercises the protocol over real HTTPS, using TypeScript/Jest in the existing `gravitee-am-test` project.

This phase is the single auditable place that proves every section of the AAUTH specification is implemented correctly. It is the equivalent of how `gravitee-am-test/specs/gateway/saml2/` and `gravitee-am-test/specs/gateway/scim/` cover SAML2 and SCIM today.

## Scope

- **Language and tooling**: TypeScript, Jest, Supertest -- NOT Java. This is a separate project from the Java handler module.
- **Location**: `gravitee-am-test/specs/gateway/aauth/` plus reusable helpers under `gravitee-am-test/api/commands/gateway/aauth-commands.ts` and key fixtures under `gravitee-am-test/api/fixtures/aauth-keys.ts`.
- **Target**: a running Gravitee AM instance (gateway at `localhost:8092`, management API at `localhost:8093`) -- the same setup used by every other E2E test in the project.
- **Domains**: each test creates its own domain via the Management API, configures `aauth.enabled = true` plus per-test policy settings, runs the test, and cleans up via `safeDeleteDomain` in `afterAll`.
- **Mocks**: agents, resources, and remote auth servers are simulated by minimal Node HTTP servers run inside the test process. Gravitee AM is the only "real" component.

## Discovery

**Existing E2E conventions** (already in `gravitee-am-test/`):
- `npm test <spec-file>` runs a single test, `npm run ci:gateway` runs the gateway suite.
- Tests live under `specs/gateway/<protocol>/*.jest.spec.ts`.
- HTTP via Supertest fluent API: `performPost(url, '', body, headers).expect(200)`.
- Management commands in `api/commands/management/`: `requestAdminAccessToken`, `createDomain`, `startDomain`, `safeDeleteDomain`, `waitForDomainSync`, `waitForDomainStart`, `uniqueName`.
- Gateway commands in `api/commands/gateway/`: `performPost`, `performGet`, `performOptions`, `performDelete`, `performFormPost`.
- JWT helpers in `api/commands/utils/jwt.ts`.

**Spec references for Phase 16** (cross-cutting):
- AAUTH Protocol spec (2026-04-09): [https://github.com/dickhardt/AAuth](https://github.com/dickhardt/AAuth)
- HTTP Signature Headers spec: [https://dickhardt.github.io/signature-key/](https://dickhardt.github.io/signature-key/)
- RFC 9421, RFC 9530, RFC 8941, RFC 7638

## Design

### Directory layout

```
gravitee-am-test/
├── api/
│   ├── commands/
│   │   ├── gateway/
│   │   │   └── aauth-commands.ts          (new -- AAUTH HTTP signature + token helpers)
│   │   └── management/
│   │       └── aauth-domain-commands.ts   (new -- helpers to PATCH AAuthSettings)
│   └── fixtures/
│       └── aauth-keys.ts                  (new -- Ed25519 + P-256 test keypairs)
└── specs/
    └── gateway/
        └── aauth/                          (new directory)
            ├── metadata.jest.spec.ts
            ├── signatures-hwk.jest.spec.ts
            ├── signatures-jwks.jest.spec.ts
            ├── signatures-jwt-scheme.jest.spec.ts
            ├── ps-token-endpoint.jest.spec.ts
            ├── deferred-authorization.jest.spec.ts
            ├── token-exchange.jest.spec.ts
            ├── clarification-chat.jest.spec.ts
            ├── permission-endpoint.jest.spec.ts
            └── error-responses.jest.spec.ts
```

### Helper library: `aauth-commands.ts`

A single TypeScript module exposing the building blocks needed by every spec test. Functions:

| Function | Purpose |
|----------|---------|
| `generateKeyPair(alg: 'Ed25519' \| 'P-256')` | Generate a fresh keypair for an in-test agent or resource. |
| `jwkThumbprint(publicKey)` | Compute RFC 7638 JWK Thumbprint. |
| `signRequest({ method, url, body?, contentType?, keyPair, scheme, schemeParams })` | Build RFC 9421 `Signature-Input`, `Signature`, `Signature-Key` (and `Content-Digest` if body), where `scheme` is one of `'hwk' \| 'jwks_uri' \| 'jwt'`. Returns `{ signature, signatureInput, signatureKey, contentDigest? }`. |
| `signedPost(url, body, keyPair, scheme, schemeParams)` | Convenience wrapper that calls `performPost` with the headers from `signRequest`. |
| `signedGet(url, keyPair, scheme, schemeParams)` | Same for GET. |
| `signedDelete(url, keyPair, scheme, schemeParams)` | Same for DELETE. |
| `buildResourceToken({ iss, dwk, aud, agent, agentJkt, scope, exp }, signingKey)` | Mints a `aa-resource+jwt` token used by mock resources. |
| `buildAgentToken({ iss, dwk, sub, cnfJwk, exp, audSub? }, signingKey)` | Mints an `aa-agent+jwt` token used by mock agent servers and federation tests. |
| `buildAuthToken({ ... }, signingKey)` | Mints an `aa-auth+jwt` (used in negative tests where the spec wants a foreign auth token). |
| `decodeJwt(jwt)` | Decode without validation. |
| `assertAuthTokenClaims(jwt, expected)` | AssertJ-style chained assertions over a decoded `aa-auth+jwt`. |
| `pollPendingUrl(url, agentKeyPair, scheme, schemeParams, options?)` | Helper that polls a pending URL until terminal response or timeout, with `Prefer: wait=N` support. |
| `parseAAuthRequirementHeader(headerValue)` | Parse the RFC 8941 dictionary into `{ requirement, url?, code?, supported_algorithms? }`. |
| `parseAAuthErrorHeader(headerValue)` | Parse the AAuth-Error header into `{ error, ...params }`. |

### Mock servers

| Mock | Purpose | Used by |
|------|---------|---------|
| `MockResourceServer` | Minimal Node HTTP server. Returns `401 + AAuth-Requirement: requirement=auth-token; resource-token="..."` for unauthenticated calls and `200` for valid auth tokens. Publishes `/.well-known/aauth-resource.json` and `/jwks.json`. | `autonomous-authorization`, `token-exchange`, `signatures-jwt-scheme` |
| `MockAgentMetadataServer` | Publishes `/.well-known/aauth-agent.json` and `/jwks.json` for an in-test agent identity. Configurable `clarification_supported`, `client_name`, `logo_uri`. | `signatures-jwks`, `clarification-chat`, `autonomous-authorization` |
| `MockRemoteAS` | Pretends to be a foreign auth server. Publishes `/.well-known/aauth-person.json`. Configurable response (200 with auth token, 202 with deferred, 4xx/5xx error, refusal). Captures the forwarded request for assertion. | `as-to-as-federation` |

All three mocks are implemented as small classes in `aauth-commands.ts`, started in `beforeAll`, stopped in `afterAll`. Each picks a free port via the same `uniqueName` mechanism used elsewhere.

### Domain provisioning helper: `aauth-domain-commands.ts`

Wraps the existing Management API client to:
- `enableAauthOnDomain(domainId, accessToken, settings?)` -- PATCH `aauth.enabled = true` plus optional settings (`authTokenLifespan`, `pendingRequestTtl`, `allowedAgentPatterns`, `trustedRemoteAuthServers`, `refreshWindowSeconds`). Does not accept any consent-related parameter; consent is handled by the OIDC pipeline AAUTH plugs into (see Phase 7 "Scope validation").
- `getAauthIssuerMetadata(domainHrid)` -- GET `/{domainHrid}/aauth/.well-known/aauth-person.json` and return parsed JSON.
- `provisionTestDomainWithAauth(accessToken, options)` -- combined helper: `createDomain` + `enableAauthOnDomain` + `startDomain` + `waitForDomainSync` + `waitForDomainStart`. Returns `{ domain, issuerMetadata }`.
- `seedScopeApproval(domainId, userId, agentIdentityUrl, scope, accessToken, opts?)` -- pre-creates a `ScopeApproval` row via the existing OIDC scope-approval Management API. The `clientId` parameter on the underlying API takes the agent identity URL as a plain string. `opts.expiresAt` defaults to "1 year from now". This is the helper E2E tests use to make a flow friction-free without going through the consent UI. Mirrors how Phase 16's docker bootstrap pre-seeds Scenario A.
- `revokeScopeApproval(domainId, userId, agentIdentityUrl, scope, accessToken)` -- inverse helper. Used by tests that exercise the "user revokes consent and the next request is deferred again" path.

### Per-spec test files

Each `*.jest.spec.ts` file follows the same skeleton:

```typescript
import { beforeAll, afterAll, describe, it, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { provisionTestDomainWithAauth } from '@management-commands/aauth-domain-commands';
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { generateKeyPair, signedPost, signedGet, ... } from '@gateway-commands/aauth-commands';

jest.setTimeout(120000);

let accessToken: string;
let domain: any;
let issuerMetadata: any;
let agentKeyPair: any;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  agentKeyPair = generateKeyPair('Ed25519');
  ({ domain, issuerMetadata } = await provisionTestDomainWithAauth(accessToken, {
    name: uniqueName('aauth-<area>', true),
    // No "consentRequiredScopes" parameter -- consent triggering in AAUTH
    // is universal for user-bound flows and is controlled per
    // (user, agent, scope) via cached ScopeApproval rows. See Phase 7.
    // ...other per-test settings (authTokenLifespan, allowedAgentPatterns, etc.)
  }));

  // For test scenarios that need a frictionless path, seed a ScopeApproval
  // BEFORE running the test. For test scenarios that need to exercise the
  // 202 deferred flow, do nothing (the cache will be empty and the deferred
  // path will run automatically).
  //
  // Example: make calendar.read frictionless for the test user
  //   await seedScopeApproval(
  //     domain.id, 'alice', 'http://agent.test:9000', 'calendar.read', accessToken);
});

afterAll(async () => {
  await safeDeleteDomain(domain?.id, accessToken);
});

describe('AAUTH <area>', () => {
  it('<spec section description>', async () => {
    // Use signedPost / signedGet / mock servers to exercise the protocol
  });
});
```

### Spec coverage matrix

Each E2E file covers a specific spec area. Together they form the complete spec proof.

| File | Spec sections covered | Java phase verified |
|------|----------------------|---------------------|
| `metadata.jest.spec.ts` | [Section 14 -- Metadata Documents](https://github.com/dickhardt/AAuth) | P1, P3 |
| `signatures-hwk.jest.spec.ts` | [Section 5 -- HMS Profile](https://github.com/dickhardt/AAuth), [Section 4.3 -- Pseudonym](https://github.com/dickhardt/AAuth), [Section 8.2 -- Replay](https://github.com/dickhardt/AAuth) | P2 |
| `signatures-jwks.jest.spec.ts` | [Section 5.2 -- jwks_uri scheme](https://github.com/dickhardt/AAuth), [Section 4.4 -- Identity](https://github.com/dickhardt/AAuth) | P3 |
| `signatures-jwt-scheme.jest.spec.ts` | Section 5.2 (jwt scheme), [Section 9 -- Agent Tokens](https://github.com/dickhardt/AAuth) | P9 |
| `ps-token-endpoint.jest.spec.ts` | PS token endpoint, resource tokens, auth tokens (three-party mode) | P6 |
| `deferred-authorization.jest.spec.ts` | Deferred responses, interaction, approval | P8 |
| `token-exchange.jest.spec.ts` | Multi-hop / call chaining | P12 |
| `clarification-chat.jest.spec.ts` | Clarification chat during consent | P10 |
| `permission-endpoint.jest.spec.ts` | Permission endpoint (first-party mode) | P13 |
| `error-responses.jest.spec.ts` | Error responses and codes | all |

### Test inventory by spec area

Concrete `it(...)` test cases for each file -- to be implemented in the same order so that each E2E test lands as soon as the corresponding Java code is merged.

#### `metadata.jest.spec.ts` (depends on P1, P3)

- `should serve /.well-known/aauth-person.json with the three required fields`
- `should return application/json content type`
- `should return cache-control public max-age 3600`
- `should return absolute URLs based on the domain base URL`
- `should NOT include any field beyond issuer/token_endpoint/jwks_uri`
- `should serve a valid JWKS at the jwks_uri`
- `should return 404 when aauth.enabled is false`

#### `signatures-hwk.jest.spec.ts` (depends on P2)

- `should accept a signed GET with sig=hwk and Ed25519 key`
- `should accept a signed GET with sig=hwk and P-256 key`
- `should accept a signed POST with valid Content-Digest`
- `should reject a POST with mismatched Content-Digest`
- `should return 401 with AAuth-Requirement requirement=pseudonym for unsigned requests`
- `should return 401 with AAuth-Error error=invalid_signature for tampered signature`
- `should return 401 for created timestamp older than 60 seconds`
- `should return 401 for created timestamp more than 60 seconds in the future`
- `should reject duplicate (key thumbprint, created) pairs (replay protection)`
- `should reject Signature/Signature-Input/Signature-Key with mismatched labels`
- `should return invalid_input AAuth-Error with required_input list when covered components are incomplete`
- `should return unsupported_algorithm AAuth-Error with supported_algorithms list`

#### `signatures-jwks.jest.spec.ts` (depends on P3)

- `should accept a signed request with sig=jwks_uri pointing to a reachable mock agent metadata server`
- `should fetch /.well-known/aauth-agent.json and then /jwks.json from the agent`
- `should cache the JWKS and not re-fetch within 60 seconds`
- `should re-fetch the JWKS once on unknown_kid before failing`
- `should return AAuth-Error error=unknown_key after the second fetch still misses`
- `should return AAuth-Error error=invalid_key when the agent metadata server is unreachable`
- `should return AAuth-Requirement requirement=identity when an HWK-only request hits an identity-requiring endpoint`

#### `signatures-jwt-scheme.jest.spec.ts` (depends on P9)

- `should accept a request signed with sig=jwt and a valid aa-agent+jwt presented in Signature-Key`
- `should accept a request signed with sig=jwt and a valid aa-auth+jwt as a credential`
- `should reject expired JWT with AAuth-Error error=expired_jwt`
- `should reject invalid JWT signature with AAuth-Error error=invalid_jwt`
- `should reject HTTP signature when the signing key does not match cnf.jwk`
- `should validate dwk=aauth-agent.json on agent tokens`
- `should validate dwk=aauth-person.json on auth tokens`
- `should propagate ps claim from agent token`

#### `ps-token-endpoint.jest.spec.ts` (depends on P6)

- `should complete the three-party flow: POST authorization_endpoint → resource_token → POST /aauth/token → auth_token → request resource with sig=jwt → 200`
- `should issue an aa-auth+jwt with all required claims (iss, dwk=aauth-person.json, aud, jti, agent, cnf.jwk, iat, exp, scope or sub)`
- `should set cnf.jwk to the agent's signing key`
- `should set aud to the resource identifier from the resource token's iss`
- `should validate the resource token's dwk = aauth-resource.json`
- `should reject a tampered resource_token with 400 invalid_resource_token`
- `should reject an expired resource_token with 400 expired_resource_token`
- `should reject when agent_jkt does not match the signer's key thumbprint`
- `should reject when agent claim does not match the verified signer identity`
- `should include Cache-Control: no-store on token responses`
- `should enforce auth token max lifetime of 1 hour`

#### `deferred-authorization.jest.spec.ts` (depends on P8)

**Spec-mandated 202 / polling behaviour:**
- `should return 202 with Location, Retry-After, Cache-Control, AAuth-Requirement headers and the spec-mandated body fields`
- `should return 202 status=pending when polling while user has not arrived`
- `should return 202 status=interacting once the user has loaded the interaction URL`
- `should return 200 with auth_token after user approves`
- `should return 403 after user denies`
- `should return 408 after pending TTL expires`
- `should return 410 after consumed`
- `should return 410 for unknown pending id`
- `should support Prefer: wait=N for long-polling`
- `should return 429 with linear backoff when polling too frequently`
- `should reject polling from a different agent (agent_jkt mismatch)`
- `should display the agent client_name and logo_uri from agent metadata in the consent page`
- `should display the justification from the token request body in the consent page`
- `should NOT include AAuth-Requirement or AAuth-Error headers on 403 responses`
- `should support requirement=approval flow without an interaction URL`

**Consent cache (reuse of OIDC ScopeApproval):**
- `should return 200 immediately when a still-valid ScopeApproval covers all requested scopes` -- pre-seed via `seedScopeApproval(domain.id, 'alice', agentIdentityUrl, 'profile.read', accessToken)` and assert the next token request returns 200 with `auth_token` and never enters the deferred flow
- `should return 202 deferred when seeded ScopeApproval has expired` -- seed with `expiresAt` in the past
- `should return 202 deferred when seeded ScopeApproval is for a different agent` -- the cache key includes the agent identity URL
- `should return 202 deferred when seeded ScopeApproval is for a different user`
- `should return 202 deferred when only some requested scopes are covered by the seeded approvals` -- multi-scope partial coverage still triggers deferred
- `should persist a new ScopeApproval after the user approves` -- run the deferred flow once, then immediately re-run the same request and assert it skips consent (cache hit)
- `should return 202 deferred again after the seeded ScopeApproval is revoked` -- seed, run frictionless, then `revokeScopeApproval(...)`, then assert subsequent request defers

**Domain-level consent bypass regression guard:**
- `should NOT have any AAuthSettings field that disables consent at the domain level` -- enables AAUTH on a fresh domain WITHOUT seeding anything, sends a user-bound request, asserts the response is 202 (deferred). The intent is to detect any future regression that re-introduces a `consentRequiredScopes`-style bypass. If a future contributor adds `consentMode = DISABLED` or `bypassConsentForTrustedAgents` to `AAuthSettings`, the test that does not set those flags would still pass; but a parallel negative test asserting the absence of those fields is in `AAuthSettingsTest.shouldNotExposeAnyConsentBypassField()` (Phase 7 unit test).

#### `token-exchange.jest.spec.ts` (depends on P12)

- `should accept resource_token + upstream_token and issue a new auth_token`
- `should set agent claim to the intermediary identity`
- `should preserve sub from the upstream token`
- `should set aud to the downstream resource identifier`
- `should set cnf.jwk to the intermediary signing key`
- `should reject when upstream_token aud does not match the requesting intermediary`
- `should reject when upstream_token is expired`
- `should still serve modes 1 and 2 unchanged when upstream_token is absent`

#### `clarification-chat.jest.spec.ts` (depends on P10)

- `should NOT show the Ask question affordance when agent did not opt in`
- `should accept clarification when agent metadata declares clarification_supported=true`
- `should accept clarification when token request body declares clarification_supported=true`
- `should deliver the user's question via the next polling response with status=pending and clarification field`
- `should include the optional timeout field`
- `should accept a clarification_response POST and return to pending state`
- `should display the agent's clarification_response on the next consent page render`
- `should accept multiple rounds of clarification`
- `should reject the 6th round per the spec recommendation of max 5`
- `should accept an updated request via POST with a new resource_token`
- `should accept a cancel via DELETE on the pending URL`
- `should return 410 Gone after cancel on subsequent polls`
- `should sanitize Markdown content in clarification and clarification_response`

#### `token-refresh.jest.spec.ts` (depends on P14)

- `should refresh an expired auth_token within the refresh window`
- `should preserve aud, scope, sub in the refreshed token`
- `should generate new iat, exp, jti`
- `should accept a refresh signed with a rotated agent key (new cnf.jwk)`
- `should reject an auth_token expired beyond the refresh window`
- `should reject an auth_token issued by another auth server`
- `should reject an auth_token whose dwk is not aauth-person.json`
- `should reject when the signer is not the agent named in the token`
- `should return 202 deferred response when policy requires re-consent on refresh`

#### `as-to-as-federation.jest.spec.ts` (depends on P15)

- `should detect federation when resource_token.aud is a foreign auth server`
- `should reject federation when the remote AS is not in the trusted list`
- `should fetch /.well-known/aauth-person.json from the remote AS`
- `should cache the remote AS metadata`
- `should mint an agent_token (aa-agent+jwt) with sub=original agent and cnf.jwk=original agent key`
- `should forward resource_token + agent_token to the remote AS token endpoint`
- `should sign the forwarded request with the local AS keys (sig=jwks_uri)`
- `should use Content-Type: application/json for the forwarded request`
- `should pass through a 200 OK response from the remote AS`
- `should propagate a 202 Accepted response with Location, Retry-After, AAuth-Requirement headers`
- `should return 503 when the remote AS is unreachable`
- `should leave modes 1, 2, 3, 5 unaffected when there is no foreign aud`

#### `error-responses.jest.spec.ts` (cross-cutting, depends on all)

- `should use the spec-mandated JSON error format {error, error_description?} for all token endpoint errors`
- `should emit AAuth-Error as an RFC 8941 dictionary`
- `should NOT include AAuth-Requirement or AAuth-Error headers on 403 responses`
- `should map every spec-defined error code to the documented HTTP status`
- One test per error code: `invalid_request`, `invalid_input`, `invalid_signature`, `unsupported_algorithm`, `invalid_key`, `unknown_key`, `invalid_jwt`, `expired_jwt`, `invalid_resource_token`, `expired_resource_token`, `invalid_agent_token`, `expired_agent_token`, `invalid_auth_token`

## Implementation order

Phase 16 is built **incrementally** alongside the Java phases:

1. **Once Phase 1 lands**: write `aauth-commands.ts` skeleton (key generation, helper signatures), `aauth-domain-commands.ts`, `aauth-keys.ts`, and the anchor `metadata.jest.spec.ts`. This proves the test infrastructure works end to end before any signature logic exists.
2. **As Phase 2 lands**: implement `signRequest` for the `hwk` scheme and write `signatures-hwk.jest.spec.ts`.
3. **As Phase 3 lands**: add `MockAgentMetadataServer` and `signatures-jwks.jest.spec.ts`. Extend `signRequest` for `jwks_uri`.
4. **As Phase 6 lands**: add `MockResourceServer`, `buildResourceToken`, and `autonomous-authorization.jest.spec.ts`.
5. **As Phase 7 lands**: extend `aauth-domain-commands.ts` with the full `AAuthSettings` helper.
6. **As Phase 8 lands**: `deferred-authorization.jest.spec.ts`.
7. **As Phase 8 lands**: `pollPendingUrl`, `parseAAuthRequirementHeader`, `deferred-authorization.jest.spec.ts`.
8. **As Phase 9 lands**: extend `signRequest` for `jwt` scheme; add `buildAgentToken`; write `signatures-jwt-scheme.jest.spec.ts`.
9. **As Phase 12 lands**: `token-exchange.jest.spec.ts`.
10. **As Phase 10 lands**: `clarification-chat.jest.spec.ts`.
11. **As Phase 13 lands**: `permission-endpoint.jest.spec.ts`.
12. **Final**: `error-responses.jest.spec.ts` cross-cutting suite to catch any error-format inconsistencies across all phases.

## Validation

### Running Phase 16 locally

Prerequisites:
- A running Gravitee AM instance (gateway at `localhost:8092`, management API at `localhost:8093`) -- typically via the existing `docker-compose.yml` in `gravitee-am-test/`.
- Admin credentials `admin/adminadmin` (the existing default).
- Node.js + `npm install` from the `gravitee-am-test/` directory.

Commands:
```bash
# Single spec
npm test specs/gateway/aauth/metadata.jest.spec.ts

# All AAUTH specs sequentially
npm test specs/gateway/aauth/

# All AAUTH specs in parallel (max 2 workers, matching the existing CI config)
npm run test:parallel -- specs/gateway/aauth/
```

CI integration: add `specs/gateway/aauth/` to the existing `npm run ci:gateway` script's pattern (or create a dedicated `ci:gateway:aauth` if isolation is preferred).

### Checklist

**Helper library and fixtures:**
- [ ] `aauth-commands.ts` exposes `signRequest` for `hwk`, `jwks_uri`, and `jwt` schemes
- [ ] `aauth-commands.ts` exposes `buildResourceToken`, `buildAgentToken`, `buildAuthToken`
- [ ] `aauth-commands.ts` exposes `pollPendingUrl` with `Prefer: wait=N` support
- [ ] `aauth-commands.ts` exposes `parseAAuthRequirementHeader` and `parseAAuthErrorHeader`
- [ ] `aauth-commands.ts` exposes `MockResourceServer`, `MockAgentMetadataServer`, `MockRemoteAS` classes
- [ ] `aauth-domain-commands.ts` exposes `enableAauthOnDomain` (no consent-bypass parameters) and `provisionTestDomainWithAauth`
- [ ] `aauth-domain-commands.ts` exposes `seedScopeApproval` and `revokeScopeApproval` for the cached-consent test paths
- [ ] `aauth-keys.ts` provides reusable Ed25519 and P-256 fixture keypairs

**Spec files:**
- [ ] `metadata.jest.spec.ts` exists and passes
- [ ] `signatures-hwk.jest.spec.ts` exists and passes
- [ ] `signatures-jwks.jest.spec.ts` exists and passes
- [ ] `signatures-jwt-scheme.jest.spec.ts` exists and passes
- [ ] `autonomous-authorization.jest.spec.ts` exists and passes

- [ ] `deferred-authorization.jest.spec.ts` exists and passes
- [ ] `token-exchange.jest.spec.ts` exists and passes
- [ ] `clarification-chat.jest.spec.ts` exists and passes
- [ ] `permission-endpoint.jest.spec.ts` exists and passes
- [ ] `error-responses.jest.spec.ts` exists and passes

**Spec coverage:**
- [ ] Every section listed in the spec coverage matrix above has at least one passing test
- [ ] Every spec-defined error code has a dedicated assertion in `error-responses.jest.spec.ts`
- [ ] The full suite runs cleanly via `npm test specs/gateway/aauth/` and cleans up all created domains
- [ ] CI configuration includes the AAUTH spec directory
