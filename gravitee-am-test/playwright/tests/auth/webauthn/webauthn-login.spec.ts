/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
  test,
  expect,
  addVirtualAuthenticator,
  simulateWebAuthnGesture,
  getCredentials,
  removeVirtualAuthenticator,
  handleConsentIfPresent,
  buildAuthorizeUrl,
  PASSWORDLESS_LINK_SELECTOR,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../../utils/test-constants';

test.describe('WebAuthn Passwordless Login', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('user can register then login with WebAuthn credential', async ({
    page,
    waApp,
    waUser,
    gatewayUrl,
  }) => {
    test.setTimeout(120_000);
    const clientId = waApp.settings.oauth.clientId;
    const authorizeUrl = buildAuthorizeUrl(gatewayUrl, clientId);

    // ---- Phase 1: Register a credential ----

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    await page.locator('#username').fill(waUser.username);
    await page.locator('#password').fill(API_USER_PASSWORD);
    await page.locator('button[type="submit"], #submitBtn').click();

    // Should redirect to WebAuthn registration (forceRegistration=true)
    await page.waitForURL(/.*webauthn\/register.*/i, { timeout: 15000 });

    // Attach virtual authenticator and register
    auth = await addVirtualAuthenticator(page);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#register-button').click();
    });

    // Verify credential was stored
    const credentials = await getCredentials(auth);
    expect(credentials).toHaveLength(1);

    // Handle consent page if present, then wait for callback
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    // Clear cookies to simulate a new session
    await page.context().clearCookies();

    // ---- Phase 2: Login using the registered credential ----

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    // Click the passwordless login link
    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await passwordlessLink.click();

    // Should arrive at WebAuthn login page
    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    // Fill username and click next — the virtual authenticator handles the assertion
    await page.locator('#username').fill(waUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Handle consent if present, then verify callback with authorization code
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });
    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toMatch(/^.+$/);
  });

  test('passwordless login fails for unregistered user', async ({
    page,
    waApp,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    // Click passwordless link
    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await expect(passwordlessLink).toBeVisible({ timeout: 10000 });
    await passwordlessLink.click();

    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    // Attach authenticator with NO credentials
    auth = await addVirtualAuthenticator(page);

    await page.locator('#username').fill('nonexistent-user');
    await page.locator('button.primary, button#login-button').click();

    // Should show error (either on the WebAuthn page or redirect back to login with error)
    const errorVisible = page.locator('.error-text, .error, [id="webauthn-error"]');
    await expect(errorVisible.first()).toBeVisible({ timeout: 10000 });
  });

  test('back to sign in link returns to login page', async ({
    page,
    waApp,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await expect(passwordlessLink).toBeVisible({ timeout: 10000 });
    await passwordlessLink.click();

    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    // Click "back to sign in"
    const backLink = page.locator('a:has-text("sign in"), a:has(span.icons:text("arrow_back"))');
    await backLink.click();

    // Should return to the standard login page
    await page.waitForURL(/.*login.*/i, { timeout: 10000 });
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
  });
});
