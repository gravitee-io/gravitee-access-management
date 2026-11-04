---
name: AM Playwright review checklist
layer: local
description: Review checks for changed Playwright code, for Copilot code review and review agents
dirs: [gravitee-am-test/playwright]
review-instructions: true
---

Review changed Playwright code under `gravitee-am-test/playwright/` against the author rules in `gravitee-am-test/playwright/AGENTS.md`, and also check the items below. Paths below are relative to `gravitee-am-test/playwright/`.

## Xray parity

- Each `linkJira()` call maps to the correct Xray test case.
- The test body matches the Xray spec's preconditions and expected outcome.
- Flag any Xray test cases in the feature area that are missing from the PR.
- If the feature were broken, would this test fail? If a different feature were broken, could it fail (a false dependency)?

## Structure

- Tests are independent (no shared mutable state).
- Spec files under 300 lines (split if needed); utils files can be longer if cohesive.

## WebAuthn

- `afterEach` calls `removeVirtualAuthenticator` and resets `auth = undefined`.
- Auth flow tests start from a clean browser: `test.use({ storageState: { cookies: [], origins: [] } })`.

## Severity

| Severity | Criteria | Action |
|---|---|---|
| **Blocker** | Test passes but doesn't verify the spec (parity violation) | Must fix before merge |
| **Major** | Conditional assertion, weak assertion, missing positive anchor | Must fix before merge |
| **Minor** | Helper not extracted, redundant timeout, naming inconsistency | Fix in same PR or follow-up |
