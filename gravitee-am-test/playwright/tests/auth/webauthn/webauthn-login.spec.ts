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
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
import { linkJira } from '../../../utils/jira';

test.describe('WebAuthn Passwordless Login', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('AM-2194: user can register then login with WebAuthn credential', async ({
    page,
    waApp,
    waUser,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2194');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);
    const clientId = waApp.settings.oauth.clientId;
    const authorizeUrl = buildAuthorizeUrl(gatewayUrl, clientId);

    // ---- Phase 1: Register a credential ----

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i);

    await page.locator('#username').fill(waUser.username);
    await page.locator('#password').fill(API_USER_PASSWORD);
    await page.locator('button[type="submit"], #submitBtn').click();

    // Should redirect to WebAuthn registration (forceRegistration=true)
    await page.waitForURL(/.*webauthn\/register.*/i);

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
    await page.waitForURL(/.*callback\?code=.*/i);

    // Clear cookies to simulate a new session
    await page.context().clearCookies();

    // ---- Phase 2: Login using the registered credential ----

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i);

    // Click the passwordless login link
    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await passwordlessLink.click();

    // Should arrive at WebAuthn login page
    await page.waitForURL(/.*webauthn\/login.*/i);

    // Fill username and click next — the virtual authenticator handles the assertion
    await page.locator('#username').fill(waUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Handle consent if present, then verify callback with authorization code
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('AM-2194: passwordless login fails for unregistered user', async ({
    page,
    waApp,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2194');
    const clientId = waApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);

    // Click passwordless link
    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await expect(passwordlessLink).toBeVisible();
    await passwordlessLink.click();

    await page.waitForURL(/.*webauthn\/login.*/i);

    // Attach authenticator with NO credentials
    auth = await addVirtualAuthenticator(page);

    await page.locator('#username').fill('nonexistent-user');
    await page.locator('button.primary, button#login-button').click();

    // Should show error (either on the WebAuthn page or redirect back to login with error)
    const errorVisible = page.locator('.error-text, .error, [id="webauthn-error"]');
    await expect(errorVisible.first()).toBeVisible();
  });

  test('AM-2194: back to sign in link returns to login page', async ({
    page,
    waApp,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2194');
    const clientId = waApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);

    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await expect(passwordlessLink).toBeVisible();
    await passwordlessLink.click();

    await page.waitForURL(/.*webauthn\/login.*/i);

    // Click "back to sign in"
    const backLink = page.locator('a:has-text("sign in"), a:has(span.icons:text("arrow_back"))');
    await backLink.click();

    // Should return to the standard login page
    await page.waitForURL(/.*login.*/i);
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
  });
});
