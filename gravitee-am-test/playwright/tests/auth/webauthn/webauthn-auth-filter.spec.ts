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
  simulateWebAuthnGesture,
  handleConsentIfPresent,
  loginAndRegisterWebAuthn,
  removeVirtualAuthenticator,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../../utils/test-constants';

/**
 * AM-4550: Authentication filter - Without webauthn registered user (Successful attempts)
 * AM-4551: Authentication filter - Without webauthn registered user (Unsuccessful attempts)
 * AM-4552: Authentication filter - With webauthn registered user (Successful attempts)
 *
 * Tests standard password login behaviour on a WebAuthn-enabled domain for
 * users who have NOT registered a WebAuthn credential, plus confirmation that
 * a registered user can still successfully authenticate via passwordless.
 */
test.describe('WebAuthn - Authentication Filter (AM-4550, AM-4551, AM-4552)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('non-registered user can login successfully with password (AM-4550)', async ({
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

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    // User has NOT registered for WebAuthn — standard password login
    await page.locator('#username').fill(waUser.username);
    await page.locator('#password').fill(API_USER_PASSWORD);
    await page.locator('button[type="submit"], #submitBtn').click();

    // forceRegistration is on, so we land on /webauthn/register — skip it
    await page.waitForURL(/.*webauthn\/register.*/i, { timeout: 15000 });
    const skipLink = page.locator('a[href*="skipAction"], a:has-text("skip"), a:has(span.icons:text("arrow_forward"))');
    await skipLink.click();

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toBeTruthy();
  });

  test('non-registered user fails login with wrong password (AM-4551)', async ({
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

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i);

    await page.locator('#username').fill(waUser.username);
    await page.locator('#password').fill('wrong-password');
    await page.locator('button[type="submit"], #submitBtn').click();

    // Should stay on login page with an error
    await page.waitForURL(/.*login.*error.*/i, { timeout: 15000 });
    const errorText = page.locator('.error-text:not(.hide) .error, .notification-error');
    await expect(errorText.first()).toBeVisible({ timeout: 5000 });

    // Should NOT have an authorization code
    expect(page.url()).not.toContain('callback?code=');
  });

  test('registered user succeeds with passwordless login (AM-4552)', async ({
    page,
    waApp,
    waUser,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Phase 1: Register a WebAuthn credential
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Phase 2: Clear session and do passwordless login
    await page.context().clearCookies();

    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i);

    const passwordlessLink = page.locator('a:has-text("passwordless"), a:has-text("Sign in with fingerprint"), a[href*="webauthn/login"]');
    await passwordlessLink.click();
    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    await page.locator('#username').fill(waUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toBeTruthy();
  });
});
