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
  passwordlessLogin,
  removeVirtualAuthenticator,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../../utils/test-constants';
import { patchDomain, waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { waitForNextSync } from '../../../../api/commands/gateway/monitoring-commands';

/**
 * AM-2376: Passwordless - Enforce Password Usage - Within usage limit
 * AM-2379: Passwordless - Enforce Password Usage - Outside usage limit
 *
 * When "Enforce Password Usage" is enabled with a max age, the user must
 * re-authenticate with their password after the max age expires.
 * Within the limit, passwordless login should work normally.
 */
test.describe('WebAuthn - Enforce Password Usage (AM-2376, AM-2379)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
    }
  });

  test('passwordless login succeeds within enforce password max age (AM-2376)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Enable enforce password with a generous max age (1 hour = 3600 seconds)
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessEnforcePasswordEnabled: true,
        passwordlessEnforcePasswordMaxAge: 3600,
      },
    });
    await waitForNextSync(waDomain.id);
    // patchDomain causes a full route redeploy — wait for OIDC to be ready
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 15000, intervalMs: 300 });

    // Register WebAuthn credential via password login
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Clear session
    await page.context().clearCookies();

    // Passwordless login should succeed — we're well within the 1 hour limit
    await passwordlessLogin(page, auth, gatewayUrl, clientId, waUser.username);

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toBeTruthy();
  });

  test('passwordless login is blocked outside enforce password max age (AM-2379)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Register WebAuthn credential first (with enforce password disabled)
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Now enable enforce password with a max age of 1 second
    await patchDomain(waDomain.id, waAdminToken, {
      loginSettings: {
        inherited: false,
        passwordlessEnabled: true,
        passwordlessDeviceNamingEnabled: false,
        passwordlessEnforcePasswordEnabled: true,
        passwordlessEnforcePasswordMaxAge: 1,
      },
    });
    await waitForNextSync(waDomain.id);
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 15000, intervalMs: 300 });

    // Wait for the max age to expire
    await page.waitForTimeout(2000);

    // Clear session and attempt passwordless login
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

    // Enable presence simulation and click — server should reject due to expired max age
    await auth.cdpSession.send('WebAuthn.setUserVerified', {
      authenticatorId: auth.authenticatorId,
      isUserVerified: true,
    });
    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: true,
    });

    await page.locator('button.primary, button#login-button').click();

    // The server enforces password re-authentication and redirects back to the
    // webauthn login page with an error. Wait for the redirect to complete first,
    // then check the DOM — otherwise the execution context is destroyed mid-navigation.
    await page.waitForURL(/.*error=.*/i, { timeout: 15000 });

    const serverError = page.locator('.item.error-text:not(.hide) .error');
    await expect(serverError).toBeVisible({ timeout: 5000 });

    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: false,
    });

    // Verify we did NOT get an authorization code
    expect(page.url()).not.toContain('callback?code=');
  });
});
