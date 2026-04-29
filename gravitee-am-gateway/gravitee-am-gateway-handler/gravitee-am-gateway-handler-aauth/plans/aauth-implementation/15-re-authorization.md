# Phase 15: Re-authorization

## Goal

Document the re-authorization pattern that replaces the old token refresh mechanism. AAUTH does not have a separate refresh token or refresh flow. When an auth token expires, the agent obtains a fresh resource token from the resource's authorization endpoint and submits it to the PS's token endpoint — the same flow as the initial authorization.

This gives the resource a voice in every re-authorization: the resource can adjust scope, require step-up authorization, or deny access based on current policy. The PS remembers prior consent decisions within a mission so the user is not re-prompted when the agent resubmits a request for the same resource and scope.

**Spec reference:** AAUTH Protocol spec (2026-04-09): [Re-authorization section](https://github.com/dickhardt/AAuth)

## Key rules

1. When an auth token expires, the agent requests a fresh resource token from the resource's `authorization_endpoint` and submits it to the PS's `token_endpoint`. This is the same flow as the initial authorization (Phase 6).
2. When an agent rotates its signing key, all existing auth tokens are bound to the old key and can no longer be used. The agent must re-authorize by obtaining fresh resource tokens.
3. Agents SHOULD proactively obtain a new agent token and refresh all auth tokens before the current agent token expires.
4. Auth tokens MUST NOT have an `exp` value that exceeds the `exp` of the agent token used to obtain them. A resource MUST reject an auth token whose associated agent token has expired.
5. The PS SHOULD remember prior consent decisions within a mission so the user is not re-prompted when the agent resubmits a request for the same resource and scope.

## Implementation

No new endpoints or handlers. Re-authorization uses the Phase 6 PS token endpoint and the Phase 8 deferred flow exactly as-is. The only implementation concern is:

- **Consent cache awareness**: the `ScopeApproval` cache (Phase 7 scope validation) already handles repeat consent lookups. Within a mission context, the PS can additionally check mission-level pre-approvals.
- **Agent token lifetime enforcement**: the PS must verify that the auth token it issues has `exp <= agent_token.exp`. This is a validation rule in the `AAuthTokenService`.

## Validation

- [ ] An expired auth token cannot be used to access a resource (resource rejects it)
- [ ] The agent can obtain a fresh resource token and submit it to the PS to get a new auth token
- [ ] The PS does not re-prompt the user for consent if a valid `ScopeApproval` exists for the same (user, agent, scope) tuple
- [ ] Auth token `exp` does not exceed the agent token's `exp`
- [ ] Within a mission context, the PS remembers prior consent and skips the interaction flow on re-authorization
