---
name: AM contracts
layer: local
description: OpenAPI, SDK and contract-test steps
---

# AM contracts

When API behavior, endpoints, DTOs, or annotations change:

1. Update the OpenAPI spec (`docs/mapi/openapi.yaml`).
2. Regenerate the SDK/clients as required.
3. Add or update contract/API tests covering the change (success + error paths).

OpenAPI change checklist: `docs/agent-standards/templates/openapi_change_checklist.template.md`
