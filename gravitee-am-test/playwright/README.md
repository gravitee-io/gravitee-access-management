# Playwright UI Tests — Gravitee Access Management

End-to-end UI tests for the AM Console (Angular SPA) using [Playwright](https://playwright.dev/).

This test suite lives inside `gravitee-am-test/` and directly reuses the existing API command layer (`api/commands/management/*`) and OpenAPI-generated SDK (`api/management/`). No duplicate API client code.


## Quick Start

### Prerequisites

- Node.js 18+ (same as the Jest test suite)
- AM running locally: Console on `:4200`, Management API on `:8093`, Gateway on `:8092`
- Playwright browsers installed

### Setup

```bash
cd gravitee-am-test

# Install dependencies (includes Playwright alongside Jest)
npm install

# Install Playwright browsers (first time only)
npx playwright install chromium
```

### Run Tests

```bash
# Run all Playwright tests (default workers, headless)
npm run pw

# Run in single worker, serial mode (most reliable for local dev)
npx playwright test --workers=1

# Run a specific test file
npx playwright test --workers=1 playwright/tests/security/certificates/fallback.spec.ts

# Run a specific test suite folder
npx playwright test --workers=1 playwright/tests/oauth/

# Run with visible browser
npm run pw:headed

# Run with Playwright UI mode (interactive, best for developing tests)
npm run pw:ui

# Run with step-by-step debugging
npm run pw:debug

# Run a specific test suite
npm run pw:auth
npm run pw:domain
npm run pw:application

# View the HTML test report
npm run pw:report

# Run in CI mode (3 workers, JUnit output, retries)
npm run pw:ci
```

> **Tip:** If you're seeing many failures, start with `--workers=1` to rule out parallel contention. The Docker stack (especially management API) can become slow under concurrent load.

### Environment Variables

Same variables as the Jest tests — configure once, use everywhere.

| Variable | Default | Description |
|---|---|---|
| `AM_UI_URL` | `http://localhost:4200` | AM Console URL |
| `AM_MANAGEMENT_URL` | `http://localhost:8093` | Management API URL |
| `AM_MANAGEMENT_ENDPOINT` | `{AM_MANAGEMENT_URL}/management` | Full management API endpoint |
| `AM_GATEWAY_URL` | `http://localhost:8092` | Gateway URL |
| `AM_GATEWAY_NODE_MONITORING_URL` | `http://localhost:18092/_node` | Gateway monitoring endpoint (used by `waitForDomainSync`) |
| `AM_ADMIN_USERNAME` | `admin` | Admin username |
| `AM_ADMIN_PASSWORD` | `adminadmin` | Admin password |
| `AM_DEF_ORG_ID` | `DEFAULT` | Default organization ID |
| `AM_DEF_ENV_ID` | `DEFAULT` | Default environment ID |
| `AM_DEF_ENV_HRID` | `default` | Default environment hrid (used in Angular routes — **lowercase**) |

Override for a different environment:

```bash
AM_UI_URL=https://staging.gravitee.io AM_MANAGEMENT_URL=https://staging-api.gravitee.io npm run pw
```


## Project Structure

```
gravitee-am-test/
├── playwright.config.ts           # Playwright configuration
├── api/                           # SHARED with Jest — management commands, gateway helpers, SDK models
│
└── playwright/
    ├── fixtures/                  # Test scoping and lifecycle
    │   ├── global.setup.ts        #   One-time admin login, saves browser state
    │   ├── base.fixture.ts        #   Per-suite fixtures: domain, app, user (auto-cleaned)
    │   └── ...                    #   Feature-specific fixture factories
    ├── pages/                     # Page Object Models — one per AM Console view
    │   ├── base.page.ts           #   Angular-aware waits, snackbar, dialog helpers
    │   └── ...                    #   Feature-specific page objects
    ├── utils/                     # Shared helpers
    │   ├── jest-globals-shim.ts   #   Bridges @jest/globals → Playwright expect
    │   └── ...                    #   Selectors, constants, protocol helpers
    └── tests/                     # Specs organized by feature area
        ├── auth/                  #   Login flow
        ├── oauth/                 #   Token exchange, trusted issuers
        ├── security/              #   Certificates
        └── ...                    #   Application, domain, user management
```


## Architecture Decisions

### Why Playwright Lives in gravitee-am-test

The existing `api/commands/management/` layer provides battle-tested functions for creating domains, applications, users, identity providers, and more — all backed by an auto-generated OpenAPI SDK. Keeping Playwright in the same package means:

- **Single API client** — no parallel abstraction to maintain. Aligns with the AM-5968 epic goal.
- **Shared types** — `Domain`, `Application`, `User` etc. from `api/management/models/` are used in both Jest and Playwright.
- **Same env vars** — `AM_MANAGEMENT_URL`, `AM_ADMIN_USERNAME`, etc. are already wired throughout the API commands.
- **Natural evolution** — Playwright replaces Cypress in the same location, following the same patterns.

### API-First Setup, UI-First Assertions

Tests follow this principle: create test data via the Management API (fast, reliable, deterministic), then verify through the UI (the thing we're actually testing).

```typescript
test('should show application created via API in the UI list', async ({ homePage, testDomain, testApplication }) => {
  // testDomain and testApplication are created via API in the fixtures
  // The test only exercises the UI
  await homePage.navigateToDomain(testDomain.name);
  // ... navigate to applications and assert testApplication is visible
});
```

This avoids flaky UI-driven setup (filling forms, waiting for saves) while focusing test assertions on what matters — does the UI render correctly?

### Authentication via storageState

The `global.setup.ts` file logs in once as admin and saves the browser cookies to `fixtures/.auth/admin.json`. All subsequent tests load this saved state, skipping the login flow entirely. This is Playwright's [recommended auth pattern](https://playwright.dev/docs/auth) and cuts test execution time significantly.

Tests that need to test the login flow itself override this:

```typescript
test.use({ storageState: { cookies: [], origins: [] } });
```

### Jest Globals Shim

The existing API commands import `{ expect } from '@jest/globals'` for inline assertions. This crashes outside Jest. The `register-paths.ts` hook intercepts this import and redirects it to `jest-globals-shim.ts`, which re-exports Playwright's `expect` instead. This is a bridge solution — the long-term fix (per AM-5968) is to refactor the command layer to separate API calls from test assertions.


## Writing Tests

### Creating a New Test File

Every test file should import from the custom fixture, not directly from `@playwright/test`:

```typescript
// ✅ Correct — gives you adminToken, testDomain, homePage, etc.
import { test, expect } from '../../fixtures/base.fixture';

// ❌ Wrong — loses access to custom fixtures
import { test, expect } from '@playwright/test';
```

### Using Fixtures

Fixtures handle setup and teardown automatically. Declare what your test needs:

```typescript
// Test gets a fresh domain, cleaned up after the test finishes
test('my test', async ({ page, testDomain }) => {
  // testDomain.id, testDomain.name, testDomain.hrid are available
});

// Test gets a domain AND an application within it
test('my test', async ({ page, testDomain, testApplication }) => {
  // Both are cleaned up automatically
});

// Test gets just the admin token for custom API calls
test('my test', async ({ adminToken }) => {
  // Use adminToken with the existing API commands directly
});
```

### Available Fixtures

| Fixture | Type | Description |
|---|---|---|
| `adminToken` | `string` | Admin access token (same as `requestAdminAccessToken()`) |
| `homePage` | `HomePage` | Pre-authenticated home page object |
| `loginPage` | `LoginPage` | Login page object (for auth-specific tests) |
| `testDomain` | `Domain` | Fresh domain, started and synced, auto-cleaned |
| `testApplication` | `Application` | Fresh SERVICE-type application in `testDomain`, auto-cleaned |
| `testAgenticApp` | `Application` | Fresh AGENT-type application in `testDomain`, auto-cleaned |
| `testUser` | `User` | Fresh user in `testDomain`, auto-cleaned |

### Adding a New Fixture

Add fixtures in `fixtures/base.fixture.ts` following this pattern:

```typescript
testUser: async ({ adminToken, testDomain }, use) => {
  // SETUP — use existing API commands
  const user = await createUser(testDomain.id, adminToken, {
    firstName: 'Test',
    lastName: 'User',
    email: 'test@example.com',
    username: uniqueName('pw-user'),
    password: 'P@ssw0rd123!',
  });

  // PROVIDE to the test
  await use(user);

  // TEARDOWN — always clean up, ignore errors
  try {
    await deleteUser(testDomain.id, adminToken, user.id);
  } catch {
    // May already be deleted
  }
},
```

### Creating a Page Object

Page objects extend `BasePage` which provides Angular-aware waits and common helpers:

```typescript
import { Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

export class DomainSettingsPage extends BasePage {
  // Locators — define as getters for lazy evaluation
  get generalTab(): Locator {
    return this.page.locator('a').filter({ hasText: /general/i });
  }

  get domainNameInput(): Locator {
    return this.page.getByLabel('Name');
  }

  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save' });
  }

  // Actions — compound user interactions
  async goto(domainHrid: string): Promise<void> {
    await this.navigate(`/environments/default/domains/${domainHrid}/settings`);
  }

  async updateDomainName(newName: string): Promise<void> {
    await this.domainNameInput.clear();
    await this.domainNameInput.fill(newName);
    await this.saveButton.click();
    await this.expectSnackbar('updated');   // inherited from BasePage
  }
}
```

### Page Object Rules

- **One file per page/view.** Group related locators and actions together.
- **Locators as getters.** Use `get myElement()` so Playwright evaluates them lazily.
- **No assertions in page objects.** Page objects perform actions and expose locators. Tests make assertions.
- **Inherit from `BasePage`.** It provides `waitForReady()`, `clickButton()`, `fillInput()`, `expectSnackbar()`, `confirmDialog()`, and other Angular-aware helpers.
- **Use `waitForReady()` after navigation.** The Angular app needs time to bootstrap. `BasePage.navigate()` does this automatically.


## Selector Strategy

The AM Console uses `data-testid` attributes across its Angular templates. Use this priority order for selectors:

### 1. data-testid (preferred)

Most stable, survives refactors. Use Playwright's built-in `getByTestId()`:

```typescript
const table = page.getByTestId('languagesTable');
```

Convention: camelCase, matching existing AM templates (`data-testid="textAddLanguageButton"`).

### 2. Role-Based (resilient)

Playwright's `getByRole()` uses accessibility semantics — resilient to CSS and DOM changes:

```typescript
page.getByRole('button', { name: 'Save' });
page.getByRole('heading', { name: 'Applications' });
page.getByLabel('Domain name');
```

### 3. Angular Material Selectors

For Material Design components where role-based selectors don't work well, use page-level locators (Material overlays render in `<body>`):

```typescript
// Submenu navigation helper from utils/selectors.ts
import { submenuItem } from '../utils/selectors';
const menuItem = submenuItem(page, 'Agent Metadata');

// Material components via standard selectors
page.locator('mat-option').filter({ hasText: /text/ });
page.locator('mat-select');
```

### 4. CSS Fallback (last resort)

Only when nothing else works. Document why in a comment:

```typescript
// Fallback: the login form is server-rendered by the gateway, no test IDs available
page.locator('#username');
```

### Adding data-testid to the Angular App

When writing a test for a page that lacks test IDs, add them to the Angular template:

```html
<!-- Before -->
<button mat-raised-button (click)="save()">Save</button>

<!-- After -->
<button mat-raised-button (click)="save()" data-testid="domainSaveButton">Save</button>
```

Convention: `data-testid="{feature}{element}{type}"` in camelCase. Examples: `domainSaveButton`, `applicationNameInput`, `providerTypeSelect`.


## Best Practices

### Test Independence

Every test must be independent. Never rely on state from a previous test.

```typescript
// ✅ Good — each test gets its own domain via fixture
test('test A', async ({ testDomain }) => { /* ... */ });
test('test B', async ({ testDomain }) => { /* ... */ });

// ❌ Bad — test B depends on test A's side effects
let sharedDomain;
test('test A', async () => { sharedDomain = await createDomain(...); });
test('test B', async () => { /* uses sharedDomain */ });
```

### Avoid Hard Waits

Never use `page.waitForTimeout()`. Use Playwright's auto-wait or explicit conditions:

```typescript
// ✅ Good — waits for the element to appear
await expect(page.locator('text=Domain created')).toBeVisible();

// ✅ Good — waits for a specific URL pattern
await page.waitForURL(/.*domains.*/);

// ❌ Bad — networkidle is unreliable for Angular SPAs with polling/charts
await page.waitForLoadState('networkidle');

// ❌ Bad — arbitrary delay, flaky
await page.waitForTimeout(3000);
```

### Assertions

Be specific. Test what the user sees, not internal state:

```typescript
// ✅ Good — verifies user-visible behavior
await expect(page.getByRole('heading')).toContainText('My Domain');
await expect(page.locator('mat-table')).toContainText('my-app');

// ❌ Bad — tests internal state, brittle
expect(await page.evaluate(() => window.__store.domains.length)).toBe(1);
```

### Test Naming

Use descriptive names that explain what behavior is being verified:

```typescript
// ✅ Good
test('should display error when creating domain with duplicate name', ...);
test('should navigate to application settings after creation', ...);

// ❌ Bad
test('test 1', ...);
test('domain works', ...);
```

### Error Recovery and Cleanup

Fixtures handle cleanup automatically. For manual API calls within a test, use try/finally:

```typescript
test('manual cleanup example', async ({ adminToken }) => {
  const domain = await createDomain(adminToken, 'temp', 'temp');
  try {
    // ... test logic
  } finally {
    await deleteDomain(domain.id, adminToken).catch(() => {});
  }
});
```

### Screenshots and Traces

Screenshots are captured automatically on failure. Traces are captured on first retry. To capture manually during development:

```typescript
test('debug me', async ({ page }) => {
  await page.screenshot({ path: 'playwright/test-results/debug.png' });
});
```

View trace files with:

```bash
npx playwright show-trace playwright/test-results/path-to/trace.zip
```


## Maintenance Guide

### Keeping Playwright Updated

```bash
# Update Playwright
npm install -D @playwright/test@latest

# Update browsers (required after Playwright version bump)
npx playwright install
```

Check the [Playwright changelog](https://github.com/microsoft/playwright/releases) before major version upgrades.

### When the API Layer Changes

If `api/commands/management/` or `api/management/models/` change (new endpoints, renamed fields, regenerated SDK):

- **Fixtures may need updating** — check `fixtures/base.fixture.ts` for any API call signature changes.
- **The path alias map may need updating** — if new alias prefixes are added to the root `tsconfig.json`, mirror them in `utils/register-paths.ts`.
- **Run `npm run pw -- --list`** to verify all tests still resolve correctly.

### When the Angular App Changes

- **New routes** — update page object `goto()` methods if URL patterns change.
- **Component redesign** — update the relevant page object's locators. The test specs should not change if page objects are well-designed.
- **New Material components** — add helper functions to `utils/selectors.ts` if common patterns emerge.
- **New data-testid attributes** — great! Update page objects to use them instead of fallback selectors.

### Adding a New Feature Area

Follow this checklist:

1. **Create page objects** in `playwright/pages/{feature}/` — one per page/view.
2. **Add fixtures** in `base.fixture.ts` if the feature needs API-created test data.
3. **Create test specs** in `playwright/tests/{feature}/` — organize by user workflow.
4. **Add npm scripts** in `package.json` for targeted runs: `"pw:{feature}": "playwright test playwright/tests/{feature}/"`.
5. **Add data-testid attributes** to the Angular templates as you go.

### Debugging Flaky Tests

```bash
# Run with visible browser + slowmo
npx playwright test --headed --workers=1

# Run single test with debug inspector
npx playwright test -g "test name" --debug

# Use UI mode for interactive debugging
npm run pw:ui

# View trace from a failed CI run
npx playwright show-trace path/to/trace.zip
```

### Common Failures When Running Locally

| Symptom | Cause | Fix |
|---------|-------|-----|
| All tests fail with connection errors | AM stack not running | Start the stack: `npm --prefix docker/local-stack run stack:dev:setup:mongo` |
| All tests fail with 401 | Admin credentials wrong or stack unhealthy | Check `docker ps` — management must be `healthy`. Default creds: `admin`/`adminadmin` |
| Many timeout errors in fixture setup | Parallel workers overload the management API | Run with `--workers=1` |
| UI tests fail but API tests pass | Webui container not running on `:4200` | Start it: `docker run -d --name dev-webui-1 --network dev_default -p 4200:8080 dev-webui:latest` |
| `Cannot find module '@jest/globals'` | Running from wrong directory | Run from `gravitee-am-test/`, not from `playwright/` |
| `data-testid` selectors not found | UI image outdated (missing `data-testid` attributes) | Rebuild UI: `mvn install -pl gravitee-am-ui -DskipTests` then rebuild the webui Docker image |
| Domain sync timeout | Stack is slow or restarting | Wait for `docker ps` to show `healthy` for gateway and management, then retry |

Common causes of flakiness in AM:

- **Domain sync delay** — the gateway needs time to pick up domain changes. The `testDomain` fixture calls `waitForDomainSync()` but custom setups may not.
- **Angular zone instability** — pages with continuous polling (dashboards, charts) never become "stable" per `getAllAngularTestabilities()`. `BasePage.waitForReady()` handles this by also accepting rendered content (`h1`, `ngx-datatable`, `.gv-page-container`) as a readiness signal. Always use `waitForReady()` after navigation.
- **Home page redirects** — navigating to `/` redirects through `HomeComponent` → `EnvironmentComponent` → first domain's detail page, skipping the domains list entirely. Use `homePage.gotoDomainsList()` to navigate directly to `/environments/{envHrid}/domains`.
- **Environment hrid vs ID** — Angular routes use the environment **hrid** (`default`, lowercase), not the environment **ID** (`DEFAULT`, uppercase). Using the uppercase ID renders an empty page shell with no content.
- **Mat-select overlays** — Material dropdowns render as overlays outside the component tree. Use `matOption(page, text)` from selectors.
- **Snackbar timing** — snackbars auto-dismiss. Assert quickly or increase `expectSnackbar()` timeout.
- **Parallel test name collisions** — `uniqueName()` includes both a timestamp and random characters to avoid collisions when tests run in parallel at the same millisecond.

### CI Integration

The `pw:ci` script sets `CI=true`, which changes behavior:

- Tests run with 3 workers (`workers: 3`) to balance speed and AM state isolation.
- `test.only()` causes failure (no accidentally committed debug flags).
- Retries once on failure (catches genuine flakes vs real bugs).
- Produces JUnit XML at `playwright/junit-results/junit.xml` for CI dashboard integration.
- HTML report saved to `playwright/playwright-report/` (archive as build artifact).

Example CircleCI job:

```yaml
playwright-e2e:
  docker:
    - image: mcr.microsoft.com/playwright:v1.51.1-noble
  steps:
    - checkout
    - run: cd gravitee-am-test && npm ci
    - run: cd gravitee-am-test && npm run pw:ci
    - store_test_results:
        path: gravitee-am-test/playwright/junit-results
    - store_artifacts:
        path: gravitee-am-test/playwright/playwright-report
```


## Linting

Playwright tests are linted with [eslint-plugin-playwright](https://github.com/playwright-community/eslint-plugin-playwright). The config lives in `playwright/eslint.config.mjs` and uses the `flat/recommended` preset.

```bash
# Check lint (from gravitee-am-test/)
npx eslint playwright/

# Auto-fix
npx eslint playwright/ --fix
```

**Key rules**: no `page.waitForTimeout()`, no standalone `expect()` without `await`, prefer `toBeVisible()` over `toHaveCount(1)`.

Page objects delegate assertions to test specs (`no-standalone-expect` is relaxed for `pages/` via override), since page objects should expose locators and actions, not make assertions.


## Relationship to Other Test Suites

| Suite | Purpose | Runner | Location |
|---|---|---|---|
| **Jest integration** | API-level testing (Management + Gateway) | Jest | `specs/` |
| **Playwright UI** | Browser-based UI testing | Playwright | `playwright/tests/` |
| **Cypress (legacy)** | Minimal login test | Cypress | `cypress/` |
| **Angular unit** | Component-level testing | Jest | `gravitee-am-ui/` |

The testing pyramid: Angular unit tests cover component logic, Jest integration tests cover API contracts and gateway behavior, Playwright tests cover the UI layer — user-visible behavior that only a real browser can verify.
