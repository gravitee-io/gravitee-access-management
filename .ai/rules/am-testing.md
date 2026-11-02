---
name: AM testing
layer: local
description: Test and validation rules
---

# AM testing

## Testing approach

- Cover success **and error paths** when behavior changes.
- CREATE/UPDATE/DELETE tests **must verify audit logging**.

## Step-by-step validation

After each logical step:

1. **Validate** — run tests/build.
2. **Review** — check for tidiness.
3. **Remove** — delete temporary code.
4. **Fix** — resolve linting issues.
5. **Keep repo lint/test clean** at all times.

## Test quality

- No ambiguous assertions; make assertions **specific and meaningful**.
- Remove redundant checks once a value is already asserted.

## Completion checklist

Before marking work complete:

- [ ] Tests pass
- [ ] Lint is clean
- [ ] Patterns are consistent with existing code
- [ ] Docs updated if needed
- [ ] If API layer changed → follow the OpenAPI/SDK steps in "AM contracts"
