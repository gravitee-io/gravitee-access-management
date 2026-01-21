---
name: "OpenAPI Change Checklist"
description: "Checklist template for OpenAPI updates, SDK/client regeneration, and API contract changes"
---
# OpenAPI / Contract Change Checklist

## Scope
- Change type: Behaviour | New endpoint | Modify endpoint | DTO change | Deprecation | Breaking
- Components affected:
- Backward compatible? Yes / No (if No, justification + migration plan required)

## OpenAPI updates
- [ ] Spec updated (path: <fill>)
- [ ] Request/response schemas updated
- [ ] Error schemas and status codes consistent with existing API patterns
- [ ] Security schemes correct
- [ ] Examples updated where relevant

## SDK / client regeneration
- [ ] SDK/clients regenerated
- [ ] Generated output reviewed for unexpected diffs
- [ ] Breaking changes confirmed intentional

## Tests
- [ ] Contract/API tests updated or added
- [ ] Success paths covered
- [ ] Error paths covered (validation, auth, etc.)

## Review & validation
- [ ] Canonical commands run (refer to commands.md)
- [ ] No unrelated refactors or reformatting
- [ ] Behaviour changes noted in PR description

## Notes
- Commands run:
- Files changed:
- Risks/follow-ups:
