---
name: AM integration tests
layer: local
description: Guideline files
dirs: [gravitee-am-test]
---

# AM integration tests

| Path | Description |
|------|-------------|
| `gravitee-am-test/GUIDELINES.md` | Integration test guidelines |
| `gravitee-am-test/MIGRATION_TEST_GUIDELINES.md` | Migration test guidelines (seed + upgrade/downgrade verification) |

Follow `gravitee-am-test/GUIDELINES.md` for all integration test work.

Follow `gravitee-am-test/MIGRATION_TEST_GUIDELINES.md` for any change under `gravitee-am-test/migration-seeding/` or `gravitee-am-test/specs/migration/`.

In Playwright specs under `gravitee-am-test/playwright/`, avoid brittle selectors; prefer stable test identifiers where needed.
