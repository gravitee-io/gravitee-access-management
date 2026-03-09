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
  loginAndRegisterWebAuthn,
  simulateWebAuthnGesture,
  handleConsentIfPresent,
  removeVirtualAuthenticator,
  clearSessionOnly,
  buildAuthorizeUrl,
  PASSWORDLESS_LINK_SELECTOR,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
import { linkJira } from '../../../utils/jira';
import { patchDomain, waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { waitForSyncAfter } from '../../../../api/commands/gateway/monitoring-commands';

/**
 * AM-5292: WebAuthn & Passwordless Device Recognition
 *
 * When passwordlessRememberDeviceEnabled is on, a returning user who has
 * registered a WebAuthn credential should be taken straight to the WebAuthn
 * login page (skipping the password login form).
 * When the setting is toggled off, the normal login page should appear.
 */
test.describe('WebAuthn - Device Recognition (AM-5292)', () => {
  test.use({
    storageState: { cookies: [], origins: [] },
    waExtraLoginSettings: { passwordlessRememberDeviceEnabled: true },
  });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('AM-5292: returning user skips login page when device recognition is enabled', async ({
    page,
    waApp,
    waUser,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-5292');
    const clientId = waApp.settings.oauth.clientId;

    // Register a WebAuthn credential (sets the remember-device cookie).
    // Device recognition is already enabled via waExtraLoginSettings before domain start.
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Clear ONLY the session cookie — keep the device recognition cookie
    await clearSessionOnly(page);

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));

    // Device recognition should route us directly to WebAuthn login page
    await page.waitForURL(/.*webauthn\/login.*/i);

    // Verify we're on the WebAuthn login page with the username field
    await expect(page.locator('#username')).toBeVisible();
    // The password field should NOT be present
    await expect(page.locator('#password')).toBeHidden();

    // Complete the passwordless login
    await page.locator('#username').fill(waUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('AM-5292: disabling device recognition shows normal login page for returning user', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-5292');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);
    const clientId = waApp.settings.oauth.clientId;

    // Register a credential (sets the remember-device cookie).
    // Device recognition is already enabled via waExtraLoginSettings.
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Now disable device recognition
    await waitForSyncAfter(waDomain.id, () =>
      patchDomain(waDomain.id, waAdminToken, {
        loginSettings: {
          inherited: false,
          passwordlessEnabled: true,
          passwordlessDeviceNamingEnabled: false,
          passwordlessRememberDeviceEnabled: false,
        },
      }),
    );
    await waitForOidcReady(waDomain.hrid);

    // Clear only the session cookie — device recognition cookie stays
    await clearSessionOnly(page);

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);

    // Should see the normal login form with both username AND password
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();

    // The passwordless link should still be available for manual use
    await expect(page.locator(PASSWORDLESS_LINK_SELECTOR)).toBeVisible();
  });
});
