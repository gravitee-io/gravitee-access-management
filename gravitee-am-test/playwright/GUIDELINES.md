# Playwright Test Guidelines

Standards and patterns for writing reliable Playwright tests in Gravitee AM. For project setup, running tests, and architecture decisions see [README.md](README.md).

---

## Table of Contents

1. [Fixture Lifecycle](#fixture-lifecycle)
2. [Domain Sync Patterns](#domain-sync-patterns)
3. [Gateway Auth Flow Tests](#gateway-auth-flow-tests)
4. [WebAuthn Tests](#webauthn-tests)
5. [Common Flakiness Causes](#common-flakiness-causes)
6. [Checklist](#checklist)

---

## Fixture Lifecycle

### Create Resources Before Starting the Domain

The single most important pattern for reliable tests: **create all resources (apps, users, IdPs) before starting the domain.** The initial sync picks up everything in one pass, eliminating the `waitForNextSync` race condition.

```typescript
// GOOD: create resources before start — initial sync includes everything
waDomain: async ({ waAdminToken }, use) => {
  const domain = await createDomain(waAdminToken, name, description);
  await patchDomain(domain.id, waAdminToken, { /* settings */ });
  // Do NOT start here — let gatewayUrl fixture start after app+user exist
  await use(domain);
  await safeDeleteDomain(domain.id, waAdminToken);
},

waApp: async ({ waAdminToken, waDomain }, use) => {
  const app = await createTestApp(name, waDomain, waAdminToken, 'WEB', { /* ... */ });
  await use(app);
},

gatewayUrl: async ({ waAdminToken, waDomain, waApp, waUser }, use) => {
  // App and user exist — now start the domain
  void waApp;
  void waUser;
  await startDomain(waDomain.id, waAdminToken);
  await waitForDomainSync(waDomain.id);
  await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000 });
  await use(`${baseUrl}/${waDomain.hrid}`);
},
```

```typescript
// BAD: start domain first, then create resources, then try to sync
waDomain: async ({ waAdminToken }, use) => {
  const domain = await createDomain(waAdminToken, name, description);
  await startDomain(domain.id, waAdminToken);     // started too early
  await waitForDomainSync(domain.id);              // synced without app/user
  await use(domain);
},

gatewayUrl: async ({ waDomain, waApp, waUser }, use) => {
  await waitForNextSync(waDomain.id);  // race condition — sync may already be done
  // ...
},
```

### Playwright Fixture Scoping

Playwright fixtures are **test-scoped** by default — each test gets fresh instances. Use `void fixture;` to declare dependencies without consuming the value:

```typescript
gatewayUrl: async ({ waDomain, waApp, waUser }, use) => {
  void waApp;   // ensures waApp fixture runs before this one
  void waUser;  // ensures waUser fixture runs before this one
  // ...
},
```

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

---

## Domain Sync Patterns

### Available Commands

| Command | When to Use |
|---|---|
| `waitForDomainSync(domainId)` | After `startDomain` — waits until domain is stable and synchronized |
| `waitForOidcReady(domainHrid)` | After sync — confirms OIDC discovery endpoint is live (routes deployed) |
| `waitForSyncAfter(domainId, mutation)` | **Preferred** when mutating an already-running domain (captures baseline before mutation) |
| `waitForNextSync(domainId)` | When you can't wrap the mutation; **has a race condition** — avoid when possible |

### After `patchDomain` on a Running Domain

`patchDomain` triggers a gateway redeploy. Use `waitForSyncAfter` to wrap the mutation, then `waitForOidcReady` to confirm routes are live:

```typescript
// GOOD: captures sync baseline before the mutation
await waitForSyncAfter(waDomain.id, () =>
  patchDomain(waDomain.id, waAdminToken, { loginSettings: { /* ... */ } }),
);
await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000 });
```

```typescript
// ACCEPTABLE but has a race: if sync completes before baseline capture, it may time out
await patchDomain(waDomain.id, waAdminToken, { loginSettings: { /* ... */ } });
await waitForNextSync(waDomain.id);
await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000 });
```

### After User/Credential Updates

User-level changes (e.g. `updateUsername`) do **not** require domain sync — the gateway reads user data directly from the database. However, `updateUsername` may trigger a domain event that causes a gateway route redeploy. Use `waitForOidcReady` to confirm routes are live before navigating:

```typescript
await updateUsername(domainId, adminToken, userId, newUsername);
await waitForCredentialUsernameUpdate(domainId, adminToken, userId, newUsername);
await waitForOidcReady(domain.hrid, { timeoutMs: 30000 });
```

### Anti-Patterns

```typescript
// BAD: fixed delay
await new Promise(r => setTimeout(r, 5000));

// BAD: page.waitForTimeout (Playwright lint rule violation)
await page.waitForTimeout(3000);

// BAD: waitForNextSync without waitForOidcReady after patchDomain
// Routes may still be redeploying even after sync completes
await patchDomain(domain.id, token, settings);
await waitForNextSync(domain.id);
// Missing: await waitForOidcReady(domain.hrid);
```

---

## Gateway Auth Flow Tests

### Handling Login Page Navigation

After navigating to an authorize URL, the gateway redirects to the login page. Always use an explicit timeout:

```typescript
await page.goto(authorizeUrl);
await page.waitForURL(/.*login.*/i, { timeout: 30000 });
```

### Handling Consent Pages

Use `handleConsentIfPresent` from the webauthn fixture (or implement similarly) — not all flows trigger consent:

```typescript
export async function handleConsentIfPresent(page: Page, timeoutMs = 5000): Promise<void> {
  try {
    await page.waitForURL(/.*oauth\/consent.*/i, { timeout: timeoutMs });
    await page.locator('button:has-text("Accept"), input[value="Accept"]').click();
  } catch {
    // No consent page — that's fine
  }
}
```

### Polling for Config Propagation

When a test depends on a domain setting being active on the gateway, and sync timing is uncertain, poll the actual behavior rather than relying on proxy checks:

```typescript
// Poll until the gateway reflects the expected behavior
const deadline = Date.now() + 30000;
while (true) {
  await page.goto(authorizeUrl);
  await page.waitForURL(/.*login.*/i, { timeout: 15000 });
  if (page.url().includes('webauthn/login')) break;
  if (Date.now() > deadline) {
    throw new Error('Config not propagated within 30s');
  }
  await page.waitForTimeout(1000);
}
```

---

## WebAuthn Tests

### Virtual Authenticator Lifecycle

Always clean up virtual authenticators in `afterEach`, and reset the variable to `undefined`:

```typescript
let auth: VirtualAuthenticator;

test.afterEach(async () => {
  if (auth) {
    await removeVirtualAuthenticator(auth);
    auth = undefined;  // prevent stale reference in next test
  }
});
```

### Make Cleanup Resilient

The `removeVirtualAuthenticator` helper wraps CDP calls in try/catch because the page/context may already be closed during teardown:

```typescript
export async function removeVirtualAuthenticator(auth: VirtualAuthenticator): Promise<void> {
  try {
    await auth.cdpSession.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId: auth.authenticatorId });
    await auth.cdpSession.send('WebAuthn.disable');
    await auth.cdpSession.detach();
  } catch {
    // Page/context may already be closed during teardown
  }
}
```

### WebAuthn Gesture Simulation

Use `simulateWebAuthnGesture` which enables automatic presence, triggers the action, and waits for the CDP event:

```typescript
await simulateWebAuthnGesture(auth, async () => {
  await page.locator('button.primary').click();
});
```

---

## Common Flakiness Causes

| Symptom | Cause | Fix |
|---|---|---|
| "No client found for client_id" | App not synced to gateway | Create resources before `startDomain` |
| 404 after `patchDomain` or `updateUsername` | Gateway redeploying routes | Add `waitForOidcReady` after sync |
| `waitForNextSync` timeout | Race condition — sync completed before baseline capture | Use `waitForSyncAfter` or restructure to create-before-start |
| `waitForURL(/login/)` timeout | Gateway slow under parallel load | Increase timeout to 30s |
| `cdpSession.send: Target closed` | Stale authenticator reference in `afterEach` | Reset `auth = undefined` after cleanup |
| Login page instead of expected redirect | Domain config not propagated yet | Poll actual behavior in a retry loop |
| Test timeout (no specific line) | Cumulative slow steps exceeding default 60s | Add `test.setTimeout(120000)` for multi-phase tests |

---

## Checklist

Before submitting a Playwright test:

### Fixture Design

- [ ] Resources (app, user) created **before** `startDomain`
- [ ] `gatewayUrl` fixture starts domain and waits for sync + OIDC ready
- [ ] `void fixture;` used to declare ordering dependencies
- [ ] Cleanup handles errors gracefully (try/catch)
- [ ] Uses `uniqueTestName()` for all resource names

### Sync & Readiness

- [ ] `waitForDomainSync` after `startDomain`
- [ ] `waitForOidcReady` after any domain config change
- [ ] `waitForSyncAfter` preferred over `waitForNextSync` for runtime mutations
- [ ] No raw `waitFor()` delays or `page.waitForTimeout()`

### Timeouts

- [ ] `page.waitForURL` calls have explicit timeouts (30s for login redirects)
- [ ] Multi-phase tests have `test.setTimeout(120000)` or higher
- [ ] Polling loops have deadlines (no infinite loops)

### WebAuthn

- [ ] `auth = undefined` in `afterEach` after cleanup
- [ ] `removeVirtualAuthenticator` wrapped in try/catch
- [ ] `test.use({ storageState: { cookies: [], origins: [] } })` for fresh sessions

### General

- [ ] No `test.only()` left in committed code
- [ ] Test names are descriptive
- [ ] Tests are independent (no shared mutable state between tests)
- [ ] File length under 300 lines (split if needed)
