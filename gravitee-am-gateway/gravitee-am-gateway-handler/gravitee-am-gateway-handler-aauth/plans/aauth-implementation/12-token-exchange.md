# Phase 12: Token Exchange (Call Chaining)

## Goal

Implement multi-hop call chaining where an intermediary resource acts as an agent on behalf of the original agent. The intermediary exchanges its received `auth_token` for a new `resource_token` at the next hop's resource, carrying an `upstream_token` that preserves the delegation chain. The PS validates the chain and issues a new `auth_token` scoped to the downstream resource, with nested `act` claims per RFC 8693 Section 4.1.

**Spec reference:** AAUTH Protocol spec — Call Chaining / Token Exchange sections.

> **Note:** This phase depends on Phase 9 (JWT scheme) and Phase 6 (token endpoint). The `act` claim nesting and `upstream_token` validation will be detailed once the spec's call chaining section is fully stabilized.

## Discovery

**Specification references:**
- AAUTH Protocol spec: Call Chaining / Multi-Hop sections
- AAUTH Protocol spec: Token endpoint — `upstream_token` parameter
- RFC 8693 Section 4.1 — `act` claim for delegation chains

## Design

The intermediary resource receives an `auth_token` from the calling agent, then:
1. Calls a downstream resource, which returns a `resource_token`
2. Posts to the PS token endpoint with `resource_token` + `upstream_token` (the received `auth_token`)
3. PS validates the chain: verifies `upstream_token`, checks the intermediary's identity, issues new `auth_token` with nested `act` claims

## Implementation

TBD — pending full spec stabilization of call chaining semantics.

## Validation

TBD.
