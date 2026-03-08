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
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../../utils/test-constants';
import { patchDomain, waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { waitForNextSync } from '../../../../api/commands/gateway/monitoring-commands';

const SESSION_COOKIE = 'GRAVITEE_IO_AM_SESSION';

/**
 * Clear the gateway session cookie while preserving device recognition cookies.
 * This simulates a user returning after their session expired but their device
 * is still recognized.
 */
async function clearSessionOnly(page, gatewayUrl: string) {
  const allCookies = await page.context().cookies();
  // Remove only session cookies, keep device recognition and others
  const sessionCookies = allCookies.filter((c) => c.name === SESSION_COOKIE);
  for (const cookie of sessionCookies) {
    await page.context().clearCookies({ name: cookie.name, domain: cookie.domain });
  }
}

/**
 * AM-5292: WebAuthn & Passwordless Device Recognition
 *
 * When passwordlessRememberDeviceEnabled is on, a returning user who has
 * registered a WebAuthn credential should be taken straight to the WebAuthn
 * login page (skipping the password login form).
 * When the setting is toggled off, the normal login page should appear.
 */
test.describe('WebAuthn - Device Recognition (AM-5292)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('returning user skips login page when device recognition is enabled (AM-5292)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Enable device recognition
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessRememberDeviceEnabled: true,
      },
    });
    await waitForNextSync(waDomain.id);
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000, intervalMs: 300 });

    // Phase 1: Register a WebAuthn credential (sets the remember-device cookie)
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Phase 2: Clear ONLY the session cookie — keep the device recognition cookie
    await clearSessionOnly(page, gatewayUrl);

    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    // Device recognition redirect depends on the patchDomain config being fully
    // propagated to the gateway. Poll navigation until we land on webauthn/login
    // rather than the normal login page.
    const deadline = Date.now() + 30000;
    while (true) {
      await page.goto(authorizeUrl);
      await page.waitForURL(/.*login.*/i, { timeout: 15000 });
      if (page.url().includes('webauthn/login')) break;
      if (Date.now() > deadline) {
        throw new Error('Device recognition did not redirect to webauthn/login within 30s');
      }
      await page.waitForTimeout(1000);
    }

    // Verify we're on the WebAuthn login page with the username field
    await expect(page.locator('#username')).toBeVisible();
    // The password field should NOT be present
    await expect(page.locator('#password')).not.toBeVisible();

    // Complete the passwordless login
    await page.locator('#username').fill(waUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toBeTruthy();
  });

  test('disabling device recognition shows normal login page for returning user (AM-5292)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // First enable device recognition so we get the cookie
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessRememberDeviceEnabled: true,
      },
    });
    await waitForNextSync(waDomain.id);
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000, intervalMs: 300 });

    // Register a credential (sets the remember-device cookie)
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Now disable device recognition
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessRememberDeviceEnabled: false,
      },
    });
    await waitForNextSync(waDomain.id);
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000, intervalMs: 300 });

    // Clear only the session cookie — device recognition cookie stays
    await clearSessionOnly(page, gatewayUrl);

    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i, { timeout: 15000 });

    // Should see the normal login form with both username AND password
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();

    // The passwordless link should still be available for manual use
    const passwordlessLink = page.locator('a:has-text("passwordless"), a:has-text("Sign in with fingerprint"), a[href*="webauthn/login"]');
    await expect(passwordlessLink).toBeVisible();
  });
});
