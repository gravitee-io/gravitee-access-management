# Playwright Test Guidelines

The rules for writing and reviewing Playwright tests are in [AGENTS.md](AGENTS.md) in this folder (source: `.ai/rules/am-playwright.md`) and in `.github/instructions/am-playwright-review.instructions.md`; change a rule there, not here.
This file keeps the wrong-and-right examples, fixture and helper patterns, WebAuthn, Angular traps and reference tables the rules point to. For project setup, running tests and architecture see [README.md](README.md).

---

## Table of Contents

### Part 1: Examples for the rules
1. [Assertion Quality](#1-assertion-quality)
2. [Timeout Discipline](#2-timeout-discipline)
3. [No Magic Values](#3-no-magic-values)
4. [Import from Fixture](#4-import-from-fixture)
5. [Jira Traceability](#5-jira-traceability)
6. [Xray Test Parity](#6-xray-test-parity)
7. [No networkidle](#7-no-networkidle)
8. [Positive Anchor Before Negative Assertion](#8-positive-anchor-before-negative-assertion)
9. [Assertion-First for UI State](#9-assertion-first-for-ui-state)

### Part 2: Process
11. [Test Naming Convention](#11-test-naming-convention)
12. [Implementation Protocol](#12-implementation-protocol)

### Part 3: Patterns
13. [Fixture Design](#13-fixture-design)
14. [Helper and File Organization](#14-helper-and-file-organization)
15. [Domain Sync](#15-domain-sync)
16. [Gateway Page Tests](#16-gateway-page-tests)
17. [WebAuthn / CDP Virtual Authenticator](#17-webauthn--cdp-virtual-authenticator)
18. [Angular-Specific Traps](#18-angular-specific-traps)
19. [Test Steps](#19-test-steps)

### Part 4: Reference
- [Config Defaults](#config-defaults)
- [Flakiness Causes](#flakiness-causes)
- [Background Test Execution](#background-test-execution)

---

# Part 1: Examples for the rules

## 1. Assertion Quality

### No conditional assertions

```typescript
// WRONG — hides real failures
if (res.status === 200) { expect(res.body.scope).toBe('openid'); }
else { expect(res.status).toBe(400); }

// WRONG — same problem
expect([200, 400]).toContain(res.status);

// RIGHT — assert the one expected outcome
expect(res.status).toBe(400);
```

### No `toBeDefined()` on required fields

```typescript
// WRONG — passes even if accessToken is garbage
expect(tokens.accessToken).toBeDefined();

// RIGHT — proves it's a real JWT
expect(tokens.accessToken).toMatch(JWT_FORMAT);

// WRONG — redundant when next line accesses decoded.iss
expect(decoded).toBeDefined();

// RIGHT — just access the property; it fails if undefined
expect(decoded.iss).toBe(expectedIssuer);
```

### Use specific matchers

```typescript
// WRONG
expect(code).toBeTruthy();

// RIGHT
expect(code).toMatch(AUTH_CODE_FORMAT);
```

## 2. Timeout Discipline

```typescript
// WRONG — duplicates config navigationTimeout
await page.waitForURL(/.*login.*/i, { timeout: 30000 });

// RIGHT — omit timeout, use config default
await page.waitForURL(/.*login.*/i);
```

| Constant | Value | Purpose |
|---|---|---|
| `BRIEF_TIMEOUT` | 5s | Shorter than 15s default — consent detection, quick visibility checks |
| `MULTI_PHASE_TEST_TIMEOUT` | 120s | Longer than 60s default — multi-phase flows with domain mutations |

```typescript
// RIGHT — intentionally shorter than 15s expect default
await expect(serverError).toBeVisible({ timeout: BRIEF_TIMEOUT });

// RIGHT — intentionally longer than 60s test default
test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);
```

## 3. No Magic Values

```typescript
// WRONG — magic regex repeated across files
expect(url.searchParams.get('code')).toMatch(/^[A-Za-z0-9_-]+$/);

// RIGHT — named constant
import { AUTH_CODE_FORMAT } from '../../../utils/test-constants';
expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
```

## 4. Import from Fixture

```typescript
// RIGHT — gives you custom fixtures
import { test, expect } from '../../fixtures/base.fixture';

// WRONG — loses custom fixtures
import { test, expect } from '@playwright/test';
```

For WebAuthn tests:
```typescript
import { test, expect } from '../../../fixtures/webauthn.fixture';
```

For MFA + WebAuthn tests:
```typescript
import { test, expect, MOCK_MFA_CODE } from '../../../fixtures/webauthn-mfa.fixture';
```

## 5. Jira Traceability

```typescript
import { linkJira } from '../../../utils/jira';

test('AM-4550: non-registered user can login with password', async ({ page }, testInfo) => {
  linkJira(testInfo, 'AM-4550');
  // ...
});
```

## 6. Xray Test Parity

Every Playwright test suite must be traceable to Jira/Xray test cases. Before writing tests for a new feature area, extract the Xray test specifications and use them as the source of truth for what to test.

### Workflow

1. **Extract Xray specs first** — query Jira for all test cases in the feature area (e.g., `project = AM AND type = Test AND labels = "webauthn"`). Use `scripts/jira-test-cases.sh` or the Xray GraphQL API.
2. **Map specs to tests** — each Xray test case (AM-XXXX) maps to one `test()` block with `linkJira(testInfo, 'AM-XXXX')`.
3. **Identify gaps** — any Xray test case without a corresponding Playwright test is a parity gap. Track gaps in a Jira ticket.
4. **Prioritize** — P1 (core flows, security) before P2 (edge cases, convenience).

### What parity means

- **Mapping**: every Xray test case has at least one Playwright test, and every Playwright test links back to a Jira ID. Multiple tests may share one Jira ID when a single Xray case covers multiple scenarios (e.g., positive + negative cases).

```typescript
// WRONG — linkJira present but test doesn't match the Xray spec
test('AM-2376: passwordless login works', async ({ page }, testInfo) => {
  linkJira(testInfo, 'AM-2376');
  // Xray spec says "within enforce password max age" but test doesn't configure enforce password
  await passwordlessLogin(page, auth, gatewayUrl, clientId, username);
});

// RIGHT — test matches the Xray spec exactly
test('AM-2376: passwordless login succeeds within enforce password max age', async ({
  page, waApp, waUser, gatewayUrl,
}, testInfo) => {
  linkJira(testInfo, 'AM-2376');
  const clientId = waApp.settings.oauth.clientId;
  // Enforce password is configured with 1h max age via waExtraLoginSettings fixture
  auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);
  await page.context().clearCookies();
  await passwordlessLogin(page, auth, gatewayUrl, clientId, waUser.username);
  const url = new URL(page.url());
  expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
});
```

## 7. No networkidle

```typescript
// WRONG — hangs on Angular SPAs, unreliable on gateway pages
await page.waitForLoadState('networkidle');

// RIGHT — Angular SPA: use BasePage.waitForReady()
await homePage.waitForReady();

// RIGHT — Gateway page: use waitForURL with regex
await page.waitForURL(/.*webauthn\/login.*/i);
```

## 8. Positive Anchor Before Negative Assertion

```typescript
// WRONG — vacuously true on blank page
await expect(page.locator('.thing')).toHaveCount(0);

// RIGHT — prove page rendered first, then assert absence
await expect(page.locator('#username')).toBeVisible();
await expect(page.locator('#password')).not.toBeVisible();
```

## 9. Assertion-First for UI State

```typescript
// WRONG — silently skips if checkbox is missing
if (await checkbox.isVisible()) { await checkbox.check(); }

// RIGHT — fails fast if checkbox should be there
await expect(checkbox).toBeVisible({ timeout: BRIEF_TIMEOUT });
await checkbox.check();
```

---

# Part 2: Process

## 11. Test Naming Convention

```typescript
// RIGHT — Jira ID + behavior from user's perspective
test('AM-2376: passwordless login succeeds within enforce password max age', async (...) => {

// RIGHT — negative case describes the expected failure
test('AM-2379: passwordless login is blocked outside enforce password max age', async (...) => {

// WRONG — no Jira ID, vague description
test('test enforce password', async (...) => {

// WRONG — describes implementation steps, not behavior
test('AM-2376: configure enforce password, register credential, clear cookies, login', async (...) => {
```

For a test without a Jira ID (utility or smoke test) the prefix is omitted:

```typescript
test('back to sign in link returns to login page', async (...) => {
```

## 12. Implementation Protocol

Before writing each test, follow this protocol to prevent semantic mismatches between the Xray spec and the implementation.

### Step 1: Read the Xray spec

Re-read the Jira test case at implementation time, not just during planning. Extract:
- **Preconditions** — what domain/app/user config is required?
- **Actions** — what steps does the user take?
- **Expected outcome** — what is the single observable result?

### Step 2: Map to fixtures and helpers

Identify which existing fixtures and helpers cover the preconditions and actions:

| Spec element | Maps to |
|---|---|
| "User has WebAuthn credential registered" | `loginAndRegisterWebAuthn()` in setup phase |
| "Enforce password enabled with 1h max age" | `waExtraLoginSettings` fixture property |
| "User attempts passwordless login" | `passwordlessLogin()` helper |
| "Login succeeds with authorization code" | `expect(code).toMatch(AUTH_CODE_FORMAT)` |

### Step 3: Write the test

Write the test body, then verify it matches the spec:
- Does every precondition from the spec have a corresponding fixture or setup step?
- Does the action sequence match the spec's steps?
- Does the assertion verify the spec's expected outcome — and **only** that outcome?

### Step 4: Cross-check

The questions to ask after writing the test are in the Xray parity section of `.github/instructions/am-playwright-review.instructions.md`.

---

# Part 3: Patterns

## 13. Fixture Design

### Create-before-start

```typescript
// Fixture dependency order: domain → factor → device → app → user → gatewayUrl
gatewayUrl: async ({ waAdminToken, waDomain, waApp, waUser }, use) => {
  void waApp;   // force dependency resolution
  void waUser;
  await startDomain(waDomain.id, waAdminToken);
  await waitForDomainSync(waDomain.id);
  await waitForOidcReady(waDomain.hrid);
  await use(`${baseUrl}/${waDomain.hrid}`);
},
```

```typescript
// WRONG — start before resources exist
const domain = await createDomain(token, name, desc);
await startDomain(domain.id, token);  // too early — app/user don't exist yet
```

### Fixture extraction threshold

```typescript
// WRONG — 200-line fixture inline in spec
test.describe('...', () => {
  const test = base.extend<MyFixtures>({ /* 200 lines */ });
});

// RIGHT — separate fixture file
// fixtures/webauthn-mfa.fixture.ts
export const test = base.extend<MfaWebAuthnFixtures>({ ... });
export { expect } from '@playwright/test';
```

### Cleanup

Fixture teardown runs in reverse dependency order.

```typescript
waUser: async ({ waAdminToken, waDomain }, use) => {
  const user = await createUser(waDomain.id, waAdminToken, { /* ... */ });
  await use(user);
  try {
    await deleteUser(waDomain.id, waAdminToken, user.id);
  } catch {
    // domain teardown may cascade-delete the user first
  }
},
```

### Fresh session for auth flow tests

```typescript
test.describe('WebAuthn Login', () => {
  test.use({ storageState: { cookies: [], origins: [] } });
  // ...
});
```

## 14. Helper and File Organization

### Extraction threshold

```typescript
// WRONG — 47-line helper buried in spec file
function configureTrustedIssuer(...) { /* complex setup */ }

// RIGHT — exported from shared module
import { configureTrustedIssuer } from '../../utils/token-exchange-helpers';
```

### Path aliases for Management API (preferred over `../../api/...`)

The aliases are defined in `gravitee-am-test/tsconfig.json` and resolved at Playwright runtime by `playwright/utils/register-paths.ts` (loaded first from `playwright.config.ts`). Example: `import { createDomain } from '@management-commands/domain-management-commands';`

If an alias is missing, use the correct relative depth: `../../api/...` from `playwright/fixtures/`, `../../../api/...` from `playwright/tests/<one-level>/`, `../../../../api/...` from `playwright/tests/<area>/<subarea>/`.

```
utils/webauthn-helpers.ts       ← CDP helpers, flow helpers, navigation helpers
utils/test-constants.ts         ← shared constants (regex, timeouts, passwords)
fixtures/webauthn.fixture.ts    ← fixture definition + re-exports from utils
fixtures/webauthn-mfa.fixture.ts ← MFA fixture (imports from utils)
```

### Re-export strategy

When extracting helpers from a fixture, re-export them so existing spec imports don't break:

```typescript
// fixtures/webauthn.fixture.ts
export { VirtualAuthenticator, loginAndRegisterWebAuthn, ... } from '../utils/webauthn-helpers';
```

### URL construction via helpers

```typescript
// WRONG — URL template repeated in every test
const url = `${gatewayUrl}/oauth/authorize?response_type=code&client_id=${clientId}&redirect_uri=...`;

// RIGHT
import { buildAuthorizeUrl } from '../../../utils/webauthn-helpers';
await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
```

## 15. Domain Sync

### Command reference

| Command | When to Use |
|---|---|
| `waitForDomainSync(domainId)` | After `startDomain` — waits until domain is stable |
| `waitForOidcReady(domainHrid)` | After sync — confirms OIDC discovery endpoint is live |
| `waitForSyncAfter(domainId, mutation)` | **Preferred** — wraps mutation, polls until sync advances |
| `waitForNextSync(domainId)` | Fallback — **has a race condition**, avoid when possible |

### After `patchDomain` on a running domain

```typescript
await waitForSyncAfter(waDomain.id, () =>
  patchDomain(waDomain.id, waAdminToken, { loginSettings: { /* ... */ } }),
);
await waitForOidcReady(waDomain.hrid);
```

### Anti-patterns

```typescript
// WRONG — fixed delay
await new Promise(r => setTimeout(r, 5000));

// WRONG — Playwright lint violation
await page.waitForTimeout(3000);

// WRONG — missing waitForOidcReady after patchDomain
await patchDomain(domain.id, token, settings);
await waitForNextSync(domain.id);
// Routes may still be redeploying even after sync completes
```

## 16. Gateway Page Tests

Gateway-rendered pages (login, WebAuthn register/login, consent, MFA) are static HTML served by Thymeleaf — NOT Angular SPAs. Different rules apply.

### Use `waitForURL`, not `waitForReady`

```typescript
await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
await page.waitForURL(/.*login.*/i);
```

### OAuth callback and consent handling

```typescript
import { reachOAuthAuthorizationCallback } from '../../../utils/oauth-callback-helpers';
// or from the area fixture barrel (e.g. webauthn.fixture, mfa-enrollment-matrix.fixture)

await reachOAuthAuthorizationCallback(page);
```

Use `handleConsentIfPresent` only when you need to click consent mid-flow without waiting for the callback (rare). Composite helpers (`loginAndRegisterWebAuthn`, `fullMfaLogin`, etc.) call `reachOAuthAuthorizationCallback` internally.

### Navigation helpers for non-deterministic routing

```typescript
// WRONG — conditional logic in spec body
await page.goto(authorizeUrl);
if (page.url().includes('webauthn/login')) { /* ... */ }
else { await page.locator(PASSWORDLESS_LINK_SELECTOR).click(); }

// RIGHT — named helper handles routing variance
await navigateToWebAuthnLogin(page, gatewayUrl, clientId);
```

The conditional in the helper handles infrastructure non-determinism, not a feature under test.

### Second authorisation in the same browser context

If a test runs **two** OAuth round-trips on the same `page` / context, the gateway may still hold an **end-user session** after the first callback. A second `goto(authorizeUrl)` can then skip `/login` and sometimes **skip step-up MFA**, going straight to the client **callback with `code`**. Calling `reachOAuthAuthorizationCallback` when you still expect `/mfa/challenge` or `/login` will return immediately on the short-circuited callback — leading to timeouts on the next assertion.

To get a fresh login or MFA challenge on the second leg, you can also use a new context instead of clearing cookies. The Xray parity example under [§6](#6-xray-test-parity) uses the same pattern for a second login path.

## 17. WebAuthn / CDP Virtual Authenticator

WebAuthn tests use Chrome DevTools Protocol (CDP) to simulate FIDO2 authenticators. Chromium only.

### Create authenticator

```typescript
const cdpSession = await page.context().newCDPSession(page);
await cdpSession.send('WebAuthn.enable');
const { authenticatorId } = await cdpSession.send('WebAuthn.addVirtualAuthenticator', {
  options: {
    protocol: 'ctap2', transport: 'internal', hasResidentKey: true,
    hasUserVerification: true, isUserVerified: true,
    automaticPresenceSimulation: false,
  },
});
```

### Simulate gesture

Use `simulateWebAuthnGesture` which enables automatic presence, triggers the action, waits for the CDP event, then disables auto-presence:

```typescript
await simulateWebAuthnGesture(auth, async () => {
  await page.locator('button.primary, button#register-button').click();
});
```

### Cleanup in afterEach

Always clean up virtual authenticators and reset the variable:

```typescript
let auth: VirtualAuthenticator;

test.afterEach(async () => {
  if (auth) {
    await removeVirtualAuthenticator(auth);
    auth = undefined;
  }
});
```

The `removeVirtualAuthenticator` helper wraps CDP calls in try/catch because the page/context may already be closed during teardown.

### Composite flow helpers

Use the shared helpers for common multi-step flows:

| Helper | Flow |
|---|---|
| `loginAndRegisterWebAuthn(page, gatewayUrl, clientId, username, password)` | Login → WebAuthn register → callback (`reachOAuthAuthorizationCallback`) |
| `passwordlessLogin(page, auth, gatewayUrl, clientId, username)` | Authorize → login → passwordless link → WebAuthn login → callback |
| `fullLoginWithMfaAndWebAuthn(page, gatewayUrl, clientId, username, password, mfaCode, rememberDevice)` | Login → WebAuthn register → MFA enroll → MFA challenge → callback |
| `reachOAuthAuthorizationCallback(page)` | Poll URL for `callback?code=`; handle consent between hops (use instead of `waitForURL(/callback?code=/)`) |
| `navigateToWebAuthnLogin(page, gatewayUrl, clientId)` | Navigate to WebAuthn login (handles non-deterministic routing) |
| `clearSessionOnly(page)` | Clear session cookie only (preserve device recognition) |

## 18. Angular-Specific Traps

These apply to Angular Console UI tests (not gateway page tests).

### ARIA attributes over CSS classes

Angular Material CSS class names change between versions. Use semantic attributes:

```typescript
// WRONG
await expect(option).toHaveClass(/mat-mdc-option-disabled/);

// RIGHT
await expect(option).toHaveAttribute('aria-disabled', 'true');
```

### `*ngIf` + `clear()` destroys input

When an input has `*ngIf="user.field"` with `[(ngModel)]`, `.clear()` empties the model → falsy → `*ngIf` removes the element.

```typescript
// WRONG — element disappears mid-operation
await input.fill('NewValue');

// RIGHT — select all, type over
await input.click({ clickCount: 3 });
await input.pressSequentially('NewValue');
```

### Material overlays are page-level

`mat-select` dropdowns render in `<body>` via CDK overlay, outside the component tree:

```typescript
// WRONG — scoped to component
component.locator('mat-option')

// RIGHT — page-level
page.locator('mat-option').filter({ hasText: /text/ })
```

### Snackbar overlap

Consecutive saves produce overlapping snackbars. Use `.last()` and wait for dismiss:

```typescript
const snackbar = page.locator('simple-snack-bar').last();
await expect(snackbar).toContainText(text);
await snackbar.waitFor({ state: 'hidden' });
```

### Environment hrid vs ID

Routes use **hrid** (`default`, lowercase). Management API uses **ID** (`DEFAULT`, uppercase). Using uppercase ID renders an empty page shell with no errors.

## 19. Test Steps

```typescript
test('loads the widgets and reloads them for a new time range', async ({ page, testDomain }) => {
  await test.step('open the dashboard', async () => {
    await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains/${testDomain.hrid}/dashboard`);
    await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible();
  });

  await test.step('choose another time range', async () => {
    await page.getByRole('combobox', { name: 'Time range' }).click();
    await page.getByRole('option', { name: 'Last 90 days' }).click();
  });
});
```

Keep setup that every step shares, such as a request listener, above the first step. Keep a `try`/`finally` cleanup outside the steps.

---

# Part 4: Reference

## Config Defaults

From `playwright.config.ts`:

| Setting | Value | Notes |
|---|---|---|
| `timeout` | 60s | Per-test timeout |
| `expect.timeout` | 15s | Assertion auto-retry timeout |
| `actionTimeout` | 15s | Click, fill, check actions |
| `navigationTimeout` | 30s | `goto()`, `waitForURL()` |
| `workers` | 3 (CI) / auto (local) | |
| `retries` | 1 (CI) / 0 (local) | |
| `trace` | on-first-retry | |
| `screenshot` | only-on-failure | |

## Flakiness Causes

| Symptom | Cause | Fix |
|---|---|---|
| "No client found for client_id" | App not synced to gateway | Create resources before `startDomain` |
| 404 after `patchDomain` | Gateway redeploying routes | Add `waitForOidcReady` after sync |
| `waitForNextSync` timeout | Race — sync done before baseline | Use `waitForSyncAfter` |
| `waitForURL(/login/)` timeout | Gateway slow under parallel load | Check gateway health, use `--workers=1` locally |
| `cdpSession.send: Target closed` | Stale authenticator ref | Reset `auth = undefined` after cleanup |
| Login page instead of expected redirect | Domain config not propagated | Use `waitForSyncAfter` + `waitForOidcReady` |
| Test timeout (no specific line) | Cumulative slow steps > 60s | `test.setTimeout(MULTI_PHASE_TEST_TIMEOUT)` |
| All tests timeout after one passes | Gateway OOM | See [Gateway OOM](README.md#gateway-oom-under-parallel-load) |
| 400 errors, fields silently missing | Native fetch instead of cross-fetch | Ensure `base.fixture.ts` sets `globalThis.fetch = crossFetch` |
| "Element detached from DOM" | `*ngIf` + `.clear()` removes element | Use triple-click + `pressSequentially()` |
| `waitForURL(/mfa\/challenge/)` timeout on second OAuth | Prior leg left an AM session; second `/authorize` hits callback without step-up | `await page.context().clearCookies()` before the second authorisation (see [Gateway Page Tests §16](#16-gateway-page-tests)) |
| `waitForURL(/callback?code=/)` timeout; page shows gravitee.io 404 | Redirect URI is external; `waitForURL` waits for `load` on marketing site | Use `reachOAuthAuthorizationCallback` (polls URL for `code=`) |
| `Cannot find module '.../api/commands/...'` when loading spec | Wrong relative depth: `../../api` from `playwright/tests/<area>/` resolves under `playwright/`, not `gravitee-am-test/api` | **Prefer aliases** (`@management-commands/...`, `@utils-commands/...`, `@management-models/...`) — same as Jest; resolved at runtime via `playwright/utils/register-paths.ts`. If using relatives: from `playwright/tests/<one-level>/` use `../../../api/...`; from `playwright/fixtures/` use `../../api/...` |
| Themed `img.logo` still shows `gravitee-logo.svg` | Theme updated only after the gateway loaded the domain context | Set logo and colour fields **before** `startDomain`, or wait for theme propagation and reload the login page |

## Background Test Execution

When running tests in the background (CI or async local runs):

```bash
# WRONG — piping blocks exit
npx playwright test | tee /tmp/pw.log

# RIGHT — write to file, poll separately
npx playwright test --reporter=list > /tmp/pw.log 2>&1; echo "EXIT=$?" >> /tmp/pw.log
```

- Never pipe background Playwright commands — write to file instead
- Always use `--reporter=list` for background runs (HTML reporter blocks exit)
- Poll the log file instead of blocking on output
