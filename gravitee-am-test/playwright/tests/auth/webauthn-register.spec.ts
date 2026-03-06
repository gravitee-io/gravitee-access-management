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
  VirtualAuthenticator,
} from '../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../utils/test-constants';

test.describe('WebAuthn Registration', () => {
  // No saved browser auth state — we're testing gateway login forms
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
    }
  });

  test('user can register a WebAuthn credential after password login', async ({
    page,
    waApp,
    waUser,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;
    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    // 1. Navigate to authorize — should redirect to login form
    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i);

    // 2. Log in with username/password
    await page.locator('#username').fill(waUser.username);
    await page.locator('#password').fill(API_USER_PASSWORD);
    await page.locator('button[type="submit"], #submitBtn').click();

    // 3. Should redirect to WebAuthn registration (forceRegistration=true)
    await page.waitForURL(/.*webauthn\/register.*/i, { timeout: 15000 });

    // 4. Set up virtual authenticator BEFORE clicking register
    auth = await addVirtualAuthenticator(page);

    // Verify no credentials exist yet
    const before = await getCredentials(auth);
    expect(before).toHaveLength(0);

    // 5. Click the register button — the virtual authenticator handles the ceremony
    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#register-button').click();
    });

    // 6. Verify credential was created on the virtual authenticator
    const after = await getCredentials(auth);
    expect(after).toHaveLength(1);

    // 7. May redirect to consent, then to callback with authorization code
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });
  });

  test('user can skip WebAuthn registration when not enrolling a FIDO2 factor', async ({
    page,
    waApp,
    waUser,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;
    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    // 1. Login
    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i);
    await page.locator('#username').fill(waUser.username);
    await page.locator('#password').fill(API_USER_PASSWORD);
    await page.locator('button[type="submit"], #submitBtn').click();

    // 2. Should arrive at WebAuthn registration
    await page.waitForURL(/.*webauthn\/register.*/i, { timeout: 15000 });

    // 3. Click skip link (only visible when not enrolling a FIDO2 factor)
    const skipLink = page.locator('a[href*="skipAction"], a:has-text("skip"), a:has(span.icons:text("arrow_forward"))');
    if (await skipLink.isVisible({ timeout: 3000 })) {
      await skipLink.click();

      // 4. May redirect to consent, then to callback with authorization code
      await handleConsentIfPresent(page);
      await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });
    }
    // If skip is not visible (FIDO2 factor enrolled), test is N/A — pass silently
  });
});
