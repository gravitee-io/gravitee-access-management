---
name: AM safety
layer: local
description: Safety and compatibility rules broader than the shared rules
---

# AM safety

## Must not

- Log secrets, tokens, PII, or full request/response bodies; prefer structured logs (`key=value`).
- Run destructive commands (`docker system prune -a`, `git reset --hard`) unless explicitly requested and confirmed.

## Compatibility and correctness

- Keep public contracts backward compatible unless a breaking change is explicitly authorized.
- Do not change exception types or HTTP status/exception semantics for the same condition without approval.
- Do not widen method/class visibility without a documented reason.
