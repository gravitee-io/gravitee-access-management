# Phase 8b: Deferred Authorization Enhancements

Non-blocking enhancements to the deferred authorization flow. These are not required to progress into subsequent AAUTH phases but should be tracked for completeness and spec compliance.

## Callback URL (Batch 2 — medium complexity)

**Spec reference:** Protocol spec Section 13.5

When the agent's metadata includes `callback_endpoint`, the interaction URL should append `&callback={callback_url}`. After consent completion, the user is redirected to the callback URL instead of seeing "You can close this window."

### Implementation

- Parse `callback_endpoint` from agent metadata (already in `AgentMetadata` record)
- Store it on the pending request (new field)
- Append `&callback={callback_url}` to the interaction URL in the 202 response
- In `AAuthConsentPostEndpoint`, after approval/denial:
  - If callback URL present → redirect to `{callback}?status=approved` or `{callback}?status=denied`
  - If no callback URL → render the current static HTML page
- Sanitize the callback URL (validate scheme, prevent open redirect)

## `Prefer: wait=N` Long-Polling (Batch 3 — medium complexity)

**Spec reference:** Protocol spec Section 12.1, 12.3

The agent includes `Prefer: wait=N` in the polling request header. The server holds the connection open for up to N seconds, returning immediately when the pending request state changes.

### Implementation

- Parse `Prefer: wait=N` header in `AAuthPendingEndpoint`
- If status is PENDING or INTERACTING and `wait > 0`:
  - Set up a Vert.x timer for N seconds
  - Set up a reactive watcher on the pending request status (e.g., periodic DB check or in-memory notify)
  - Return 202 when either the timer fires or the status changes
- If status is terminal → return immediately (no wait)
- Requires a `TestClock` fixture for testing without `Thread.sleep`
- Consider capping N at a server-side maximum (e.g., 60 seconds)

## `requirement=approval` Flow (Batch 4 — high complexity, deferrable)

**Spec reference:** Headers spec Section 4.6

Distinct from `requirement=interaction`. With `requirement=approval`, the AS contacts the user directly (push notification, email) without the agent directing a user to a URL. The 202 response includes only `Location` and `Retry-After`, no `url` or `code`.

### Implementation

This requires a notification infrastructure (push, email, or webhook) that doesn't exist yet. Defer until a notification mechanism is available.

- New decision path in the token endpoint: determine whether to use `interaction` or `approval` based on domain/application configuration
- `approval` response omits `url` and `code` from `AAuth-Requirement` header
- A notification is sent to the user (implementation-defined mechanism)
- The user approves/denies via a link in the notification, which hits the pending endpoint
- The agent polls as usual

## Scope Descriptions on Consent Page (Batch 2 — medium complexity)

**Spec reference:** Protocol spec Section 12.2, resource metadata `scope_descriptions`

Display human-readable scope descriptions from the resource's metadata on the consent page, alongside the scope keys.

### Implementation

- Fetch resource metadata from `{resourceIss}/.well-known/aauth-resource.json` (already fetched during token validation for JWKS)
- Extract `scope_descriptions` map (scope key → Markdown description)
- Either:
  - Store `scope_descriptions` on the pending request (new `Map<String, String>` field — schema change for both MongoDB and JDBC)
  - Or fetch at consent render time from the resource URL (adds latency)
- Render descriptions via `MarkdownSanitizer.toSafeHtml()` in the consent template
- Update `aauth_consent.html` to show descriptions below each scope key

## Additional Unit Test Coverage

The Phase 8 plan lists ~50 specific test methods across 7 test classes. The following are not yet implemented:

### `AAuthConsentHandlerTest`
- `shouldRenderConsentPage_withAgentNameAndScopes`
- `shouldDisplayJustificationFromTokenRequest`
- `shouldDisplayClientNameAndLogoUri_fromAgentMetadata`
- `shouldSanitizeMarkdownInJustification`
- `shouldReturn410_whenInteractionCodeUnknown`

### `AAuthConsentPostEndpointTest`
- `shouldApprovePendingRequest_andReturn200` (requires authenticated user in test)
- `shouldDenyPendingRequest_andReturn200` (requires authenticated user in test)
- `shouldRedirectToCallbackEndpoint_whenAgentMetadataProvidesIt` (blocked by callback URL feature)

### `AAuthTokenEndpointDeferredBranchTest`
- Full 202 response validation (headers, body fields)
- Consent cache bypass tests

### `AAuthPendingRequestServiceTest`
- `shouldExpireAfterTtl` (needs `TestClock`)
- `shouldConsumeOnceOnCompletedRetrieval` (done)
- `shouldHonorPreferWaitHeader_forLongPolling` (blocked by long-polling feature)
- `shouldGenerateUnguessablePendingId`

### Test Fixtures
- `TestPendingRequestStore` — pre-seeded pending requests in known states
- `TestClock` — mockable clock for TTL/long-poll testing
- `TestConsentPageRenderer` — template model capture
- `TestScopeApprovalSeeder` — consent cache test setup
