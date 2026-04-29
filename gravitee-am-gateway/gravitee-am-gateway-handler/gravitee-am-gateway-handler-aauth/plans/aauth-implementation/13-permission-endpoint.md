# Phase 13: Permission Endpoint — First-Party Mode

## Goal

Implement the PS permission endpoint where agents request permission for actions not governed by a remote resource — for example, executing tool calls, writing files, or sending messages on behalf of the user. This enables agents to work with a PS before any resources support AAUTH (first-party adoption mode). When the agent operates within a mission, tools listed in `approved_tools` skip this endpoint.

The permission endpoint uses the same deferred response pattern as the PS token endpoint (Phase 6) and the same requirement responses (interaction, clarification, approval) as all other AAUTH endpoints.

**Spec reference:** [Section "Permission Endpoint"](https://github.com/dickhardt/AAuth) — marked as TODO/placeholder in the 2026-04-09 spec. This phase designs the endpoint based on the available guidance and the patterns established by the token endpoint.

> **Note:** This phase will be detailed once the spec's permission endpoint section is finalized. The current content is a structural placeholder establishing the endpoint's place in the phase dependency graph.

## Discovery

**Specification references:**
- AAUTH Protocol spec (2026-04-09): Permission Endpoint section (TODO in spec)
- AAUTH Protocol spec (2026-04-09): Deferred Responses
- AAUTH Protocol spec (2026-04-09): Missions — `approved_tools` pre-approval

## Design

TBD — pending spec finalization.

## Implementation

TBD.

## Validation

TBD.
