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

async function clearSessionOnly(page) {
  const allCookies = await page.context().cookies();
  const sessionCookies = allCookies.filter((c) => c.name === SESSION_COOKIE);
  for (const cookie of sessionCookies) {
    await page.context().clearCookies({ name: cookie.name, domain: cookie.domain });
  }
}

/**
 * AM-2370: Enforce Password + Device Recognition - Within usage limit
 * AM-2374: Enforce Password + Device Recognition - Idle
 * AM-2382: Enforce Password + Device Recognition - Outside usage limit
 *
 * When both enforce password usage and device recognition are enabled,
 * the system should respect the max age for password re-authentication
 * while still routing returning users to the WebAuthn login page.
 */
test.describe('WebAuthn - Enforce Password + Device Recognition (AM-2370, AM-2374, AM-2382)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('passwordless login succeeds within enforce password max age with device recognition (AM-2370)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Enable enforce password + device recognition with generous max age
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessRememberDeviceEnabled: true,
        passwordlessEnforcePasswordEnabled: true,
        passwordlessEnforcePasswordMaxAge: 3600,
      },
    });
    await waitForNextSync(waDomain.id);
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 15000, intervalMs: 300 });

    // Register WebAuthn credential via password login
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Clear session only — keep device recognition cookie
    await clearSessionOnly(page);

    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    await page.goto(authorizeUrl);

    // Device recognition should route us directly to WebAuthn login page
    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    await page.locator('#username').fill(waUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Within max age — passwordless should succeed
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toBeTruthy();
  });

  test('passwordless login is blocked outside enforce password max age with device recognition (AM-2382)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Register credential first (without enforce password)
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Now enable enforce password + device recognition with 1s max age
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessRememberDeviceEnabled: true,
        passwordlessEnforcePasswordEnabled: true,
        passwordlessEnforcePasswordMaxAge: 1,
      },
    });
    await waitForNextSync(waDomain.id);
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 15000, intervalMs: 300 });

    // Wait for the max age to expire
    await page.waitForTimeout(2000);

    // Clear session only — keep device recognition cookie
    await clearSessionOnly(page);

    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    await page.goto(authorizeUrl);

    // Device recognition may route us to WebAuthn login, or we may land on the normal login page.
    // Wait for whichever page we get.
    await page.waitForLoadState('networkidle');

    const currentUrl = page.url();
    if (!currentUrl.includes('webauthn/login')) {
      // Landed on normal login page — navigate to passwordless
      const passwordlessLink = page.locator('a:has-text("passwordless"), a:has-text("Sign in with fingerprint"), a[href*="webauthn/login"]');
      await passwordlessLink.click();
      await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });
    }

    await page.locator('#username').fill(waUser.username);

    await auth.cdpSession.send('WebAuthn.setUserVerified', {
      authenticatorId: auth.authenticatorId,
      isUserVerified: true,
    });
    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: true,
    });

    await page.locator('button.primary, button#login-button').click();

    // Outside max age — server should reject and redirect with error
    await page.waitForURL(/.*error=.*/i, { timeout: 15000 });

    const serverError = page.locator('.item.error-text:not(.hide) .error');
    await expect(serverError).toBeVisible({ timeout: 5000 });

    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: false,
    });

    expect(page.url()).not.toContain('callback?code=');
  });

  test('idle user on passwordless page can fall back to password login (AM-2374)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Enable enforce password + device recognition
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessRememberDeviceEnabled: true,
        passwordlessEnforcePasswordEnabled: true,
        passwordlessEnforcePasswordMaxAge: 3600,
      },
    });
    await waitForNextSync(waDomain.id);
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 15000, intervalMs: 300 });

    // Register credential
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Clear session only
    await clearSessionOnly(page);

    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent('https://gravitee.io/callback')}` +
      `&scope=openid`;

    await page.goto(authorizeUrl);

    // Device recognition should route us to WebAuthn login page
    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    // Idle state: user is on the passwordless page but does NOT interact.
    // Verify the page is functional — username field and Next button visible.
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('button.primary, button#login-button')).toBeVisible();

    // The "Back to sign in" link should be available for password fallback
    const backLink = page.locator('a:has-text("sign in"), a:has(span.icons:text("arrow_back"))');
    await expect(backLink).toBeVisible();

    // Click back to sign in — user can fall back to password login
    await backLink.click();
    await page.waitForURL(/.*login.*/i, { timeout: 10000 });

    // Verify we're back on the password login form
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
  });
});
