# Playwright Test Guidelines

Standards and patterns for writing reliable, maintainable Playwright tests in Gravitee AM. For project setup, running tests, and architecture decisions see [README.md](../README.md).

Every rule and pattern here was distilled from a real failure or review finding. Follow them to avoid re-learning the same lessons.

---

## Table of Contents

### Part 1: Rules (violation = review rejection)
1. [Assertion Quality](#1-assertion-quality)
2. [Timeout Discipline](#2-timeout-discipline)
3. [No Magic Values](#3-no-magic-values)
4. [Import from Fixture](#4-import-from-fixture)
5. [Jira Traceability](#5-jira-traceability)
6. [Xray Test Parity](#6-xray-test-parity)
7. [No networkidle](#7-no-networkidle)
8. [Positive Anchor Before Negative Assertion](#8-positive-anchor-before-negative-assertion)
9. [Assertion-First for UI State](#9-assertion-first-for-ui-state)
10. [cross-fetch Required](#10-cross-fetch-required-silent-failure)

### Part 2: Process (how to write and review tests)
11. [Test Naming Convention](#11-test-naming-convention)
12. [Implementation Protocol](#12-implementation-protocol)

### Part 3: Patterns (how to structure code)
13. [Fixture Design](#13-fixture-design)
14. [Helper and File Organization](#14-helper-and-file-organization)
15. [Domain Sync](#15-domain-sync)
16. [Gateway Page Tests](#16-gateway-page-tests)
17. [WebAuthn / CDP Virtual Authenticator](#17-webauthn--cdp-virtual-authenticator)
18. [Angular-Specific Traps](#18-angular-specific-traps)

### Part 4: Reference
- [Config Defaults](#config-defaults)
- [Flakiness Causes](#flakiness-causes)
- [Gateway OOM Under Parallel Load](#gateway-oom-under-parallel-load)
- [Background Test Execution](#background-test-execution)
- [Pre-Submit Checklist](#pre-submit-checklist)

---

# Part 1: Rules

These are non-negotiable. Violations will be caught in code review.

## 1. Assertion Quality

Every test asserts exactly one expected outcome.

### No conditional assertions

Never wrap `expect()` in `if/else` or accept multiple outcomes.

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

`toBeDefined()` proves existence, not correctness — it passes for empty strings, wrong values, garbage data.

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

Use `toBeDefined()` **only** for optional fields where absence is a valid test outcome.

### Use specific matchers

```typescript
// WRONG
expect(code).toBeTruthy();

// RIGHT
expect(code).toMatch(AUTH_CODE_FORMAT);
```

## 2. Timeout Discipline

`playwright.config.ts` defines defaults (see [Config Defaults](#config-defaults)). **Only override when intentionally different.**

```typescript
// WRONG — duplicates config navigationTimeout
await page.waitForURL(/.*login.*/i, { timeout: 30000 });

// RIGHT — omit timeout, use config default
await page.waitForURL(/.*login.*/i);
```

Only two timeout constants are needed (defined in `utils/test-constants.ts`):

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

All cross-file values belong in `utils/test-constants.ts`. Never inline regex patterns, timeouts, or passwords.

```typescript
// WRONG — magic regex repeated across files
expect(url.searchParams.get('code')).toMatch(/^[A-Za-z0-9_-]+$/);

// RIGHT — named constant
import { AUTH_CODE_FORMAT } from '../../../utils/test-constants';
expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
```

Available constants:

| Constant | Value | Use for |
|---|---|---|
| `API_USER_PASSWORD` | `SomeP@ssw0rd` | Default test user password |
| `AUTH_CODE_FORMAT` | `/^[A-Za-z0-9_-]+$/` | OAuth authorization code validation |
| `JWT_FORMAT` | Three Base64url segments with dots | JWT token validation |
| `BRIEF_TIMEOUT` | 5000 | Short expect timeout |
| `MULTI_PHASE_TEST_TIMEOUT` | 120000 | Extended test timeout |

## 4. Import from Fixture

Always import `test` and `expect` from the fixture, never from `@playwright/test` directly. The fixture provides typed fixtures (e.g., `adminToken`, `testDomain`, `waUser`).

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

Every test that maps to a Jira test case must call `linkJira()` as the first line. This enables Xray traceability.

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
- **Semantic match**: the Playwright test verifies the same behavior described in the Xray spec — same preconditions, same actions, same expected outcome. A test that passes but tests something different is not parity.
- **No fabricated coverage**: do not write a test that "covers" a Jira ID by testing a related but different behavior. If the spec says "login fails when password expired", test exactly that — not "login fails when password is wrong".

### Parity review during code review

When reviewing a PR that adds tests (see also the [Pre-Submit Checklist](#pre-submit-checklist)):
- Verify each `linkJira()` call maps to the correct Xray test case
- Verify the test body matches the Xray spec's preconditions and expected outcome
- Flag any Xray test cases in the feature area that are missing from the PR

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

Neither Angular SPAs (continuous polling) nor gateway pages should use `networkidle`. Use explicit URL or element waits.

```typescript
// WRONG — hangs on Angular SPAs, unreliable on gateway pages
await page.waitForLoadState('networkidle');

// RIGHT — Angular SPA: use BasePage.waitForReady()
await homePage.waitForReady();

// RIGHT — Gateway page: use waitForURL with regex
await page.waitForURL(/.*webauthn\/login.*/i);
```

## 8. Positive Anchor Before Negative Assertion

`toHaveCount(0)` and `not.toBeVisible()` pass instantly on an unloaded page — the element is absent because *everything* is absent.

```typescript
// WRONG — vacuously true on blank page
await expect(page.locator('.thing')).toHaveCount(0);

// RIGHT — prove page rendered first, then assert absence
await expect(page.locator('#username')).toBeVisible();
await expect(page.locator('#password')).not.toBeVisible();
```

## 9. Assertion-First for UI State

Use `await expect(element).toBeVisible()` before interacting, not `if (await element.isVisible())`. Conditionals silently skip when the element is missing.

```typescript
// WRONG — silently skips if checkbox is missing
if (await checkbox.isVisible()) { await checkbox.check(); }

// RIGHT — fails fast if checkbox should be there
await expect(checkbox).toBeVisible({ timeout: BRIEF_TIMEOUT });
await checkbox.check();
```

## 10. cross-fetch Required (Silent Failure)

ALWAYS use cross-fetch, never native Node.js fetch. Native fetch in Node 18+ causes the generated SDK to silently drop fields (e.g., `redirectUris` disappears from request body — no error, just a 400).

This is set in `base.fixture.ts` via `globalThis.fetch = crossFetch` and affects **all** tests — Angular UI, gateway pages, and API tests alike. Never remove it.

---

# Part 2: Process

How to write tests, name them, and review them. Following these ensures consistent quality across contributors.

## 11. Test Naming Convention

Test names follow the pattern: `'AM-XXXX: <behavior description>'`

- **Jira ID prefix** — the Xray test case ID, matching the `linkJira()` call
- **Behavior description** — what the test proves, not what it does. Use the user's perspective.

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

For tests without a Jira ID (utility tests, smoke tests), omit the prefix but keep the behavior description:

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

After writing, re-read the spec one more time and ask:
- If the feature were broken, would this test fail? (If not, the assertion is too weak.)
- If a different feature were broken, could this test fail? (If so, the test has a false dependency.)
- Does the `linkJira()` ID match the spec this test actually verifies?

---

# Part 3: Patterns

Follow these unless you have a documented reason not to.

## 13. Fixture Design

### Create-before-start

Create all domain resources (app, user, factor, device) before `startDomain()`. The initial sync picks up everything in one pass, eliminating race conditions.

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

Inline fixtures > 50 lines → extract to a dedicated fixture file.

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

### Scoping and dependency declaration

Playwright fixtures are test-scoped by default. Use `void fixture;` to declare ordering dependencies without consuming the value.

### Cleanup

Fixture teardown runs in reverse dependency order. Always handle cleanup errors gracefully:

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

Gateway auth flow tests need clean browser state:

```typescript
test.describe('WebAuthn Login', () => {
  test.use({ storageState: { cookies: [], origins: [] } });
  // ...
});
```

## 14. Helper and File Organization

### Extraction threshold

Helpers used by 2+ files or > 20 lines → `utils/` module. Spec files contain only test logic.

```typescript
// WRONG — 47-line helper buried in spec file
function configureTrustedIssuer(...) { /* complex setup */ }

// RIGHT — exported from shared module
import { configureTrustedIssuer } from '../../utils/token-exchange-helpers';
```

### Dependency direction

Fixtures import from utils. **Never the reverse** (avoids circular imports).

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

Never construct OAuth authorize URLs inline:

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

`patchDomain` triggers a gateway redeploy. Always use `waitForSyncAfter` + `waitForOidcReady`:

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

Gateway pages are server-rendered. Use `page.waitForURL()` with regex patterns:

```typescript
await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
await page.waitForURL(/.*login.*/i);
```

### Consent handling

Not all OAuth flows trigger a consent page. Use the shared `handleConsentIfPresent` helper:

```typescript
import { handleConsentIfPresent } from '../../../utils/webauthn-helpers';

await handleConsentIfPresent(page);  // uses BRIEF_TIMEOUT (5s) by default
```

### Navigation helpers for non-deterministic routing

Gateway routing (e.g., device recognition) may produce different redirect paths. Encapsulate infrastructure conditionals in named helpers — keep spec bodies deterministic.

```typescript
// WRONG — conditional logic in spec body
await page.goto(authorizeUrl);
if (page.url().includes('webauthn/login')) { /* ... */ }
else { await page.locator(PASSWORDLESS_LINK_SELECTOR).click(); }

// RIGHT — named helper handles routing variance
await navigateToWebAuthnLogin(page, gatewayUrl, clientId);
```

The conditional in the helper handles infrastructure non-determinism, not a feature under test. This is the **only** acceptable use of conditional navigation — never use `if/else` around assertions.

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
| `loginAndRegisterWebAuthn(page, gatewayUrl, clientId, username, password)` | Login → WebAuthn register → consent → callback |
| `passwordlessLogin(page, auth, gatewayUrl, clientId, username)` | Authorize → login → passwordless link → WebAuthn login → consent → callback |
| `fullLoginWithMfaAndWebAuthn(page, gatewayUrl, clientId, username, password, mfaCode, rememberDevice)` | Login → WebAuthn register → MFA enroll → MFA challenge → consent → callback |
| `navigateToWebAuthnLogin(page, gatewayUrl, clientId)` | Navigate to WebAuthn login (handles non-deterministic routing) |
| `clearSessionOnly(page)` | Clear session cookie only (preserve device recognition) |

## 18. Angular-Specific Traps

These apply to Angular Console UI tests (not gateway page tests). For the cross-fetch requirement (which affects all tests), see [§10](#10-cross-fetch-required-silent-failure).

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
| All tests timeout after one passes | Gateway OOM | See [Gateway OOM](#gateway-oom-under-parallel-load) |
| 400 errors, fields silently missing | Native fetch instead of cross-fetch | Ensure `base.fixture.ts` sets `globalThis.fetch = crossFetch` |
| "Element detached from DOM" | `*ngIf` + `.clear()` removes element | Use triple-click + `pressSequentially()` |

## Gateway OOM Under Parallel Load

The gateway runs with `GIO_MAX_MEM=512m` by default. Each domain creates a Spring `ApplicationContext`, so multiple workers creating domains simultaneously can trigger OOM. After OOM, the gateway never recovers — all tests timeout.

**How to identify:** A few tests pass, then everything times out. `docker ps` shows gateway `(unhealthy)`.

**Fix:** Use `--workers=1` for local dev, or set `GIO_MAX_MEM=1024m` in `docker/local-stack/dev/docker-compose.yml`.

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

## Pre-Submit Checklist

### Rules
- [ ] No conditional assertions (`if/else` around `expect`)
- [ ] No `toBeDefined()` on required fields — use type/value matchers
- [ ] No magic values — all constants in `test-constants.ts`
- [ ] No explicit timeouts that match config defaults
- [ ] `BRIEF_TIMEOUT` only where intentionally shorter than 15s
- [ ] `MULTI_PHASE_TEST_TIMEOUT` for multi-phase tests
- [ ] Imports from fixture, not `@playwright/test`
- [ ] `linkJira(testInfo, 'AM-XXXX')` on every Jira-traced test
- [ ] Xray parity: every test links to a Jira ID, and test body matches the spec
- [ ] No `networkidle`
- [ ] Positive anchor before every negative assertion
- [ ] Assertion-first (`expect().toBeVisible()`) before element interaction
- [ ] cross-fetch set in base fixture (never native Node.js fetch)

### Process
- [ ] Test name follows `'AM-XXXX: <behavior description>'` pattern
- [ ] Xray spec re-read at implementation time (not just during planning)
- [ ] Test preconditions match spec preconditions
- [ ] Test assertion matches spec expected outcome — and only that outcome
- [ ] Cross-check: would this test fail if the feature were broken?

### Patterns
- [ ] Resources created **before** `startDomain`
- [ ] `gatewayUrl` fixture starts domain and waits for sync + OIDC ready
- [ ] `void fixture;` for ordering dependencies
- [ ] Cleanup handles errors gracefully (try/catch)
- [ ] `uniqueTestName()` for all resource names
- [ ] `waitForSyncAfter` + `waitForOidcReady` after runtime domain mutations
- [ ] Helpers > 20 lines in `utils/`, not inline in spec files
- [ ] Inline fixtures > 50 lines extracted to fixture files
- [ ] URL construction via `buildAuthorizeUrl()`, not inline
- [ ] No `if/else` for navigation — use `navigateToWebAuthnLogin()` or similar helpers

### WebAuthn
- [ ] `auth = undefined` in `afterEach` after cleanup
- [ ] `removeVirtualAuthenticator` wrapped in try/catch
- [ ] `test.use({ storageState: { cookies: [], origins: [] } })` for fresh sessions

### General
- [ ] No `test.only()` in committed code
- [ ] Tests are independent (no shared mutable state)
- [ ] ESLint passes (`npx eslint playwright/`)
- [ ] Spec files under 300 lines (split if needed); utils files can be longer if cohesive

### Severity guide (for reviewers)

| Severity | Criteria | Action |
|---|---|---|
| **Blocker** | Test passes but doesn't verify the spec (parity violation) | Must fix before merge |
| **Major** | Conditional assertion, weak assertion, missing positive anchor | Must fix before merge |
| **Minor** | Helper not extracted, redundant timeout, naming inconsistency | Fix in same PR or follow-up |
