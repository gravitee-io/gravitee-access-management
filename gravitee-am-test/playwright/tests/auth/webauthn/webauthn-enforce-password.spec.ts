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
  simulateWebAuthnGesture,
  removeVirtualAuthenticator,
  buildAuthorizeUrl,
  PASSWORDLESS_LINK_SELECTOR,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../../utils/test-constants';
import { patchDomain, waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { waitForSyncAfter } from '../../../../api/commands/gateway/monitoring-commands';

/**
 * AM-2376: Passwordless - Enforce Password Usage - Within usage limit
 * AM-2379: Passwordless - Enforce Password Usage - Outside usage limit
 *
 * When "Enforce Password Usage" is enabled with a max age, the user must
 * re-authenticate with their password after the max age expires.
 * Within the limit, passwordless login should work normally.
 */
test.describe('WebAuthn - Enforce Password Usage', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test.describe('within usage limit', () => {
    test.use({
      waExtraLoginSettings: {
        passwordlessEnforcePasswordEnabled: true,
        passwordlessEnforcePasswordMaxAge: 3600,
      },
    });

    test('AM-2376: passwordless login succeeds within enforce password max age', async ({
      page,
      waApp,
      waUser,
      gatewayUrl,
    }) => {
      const clientId = waApp.settings.oauth.clientId;

      // Enforce password is configured via waExtraLoginSettings before domain start.
      // Register WebAuthn credential via password login.
      auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

      // Clear session
      await page.context().clearCookies();

      // Passwordless login should succeed — we're well within the 1 hour limit
      await passwordlessLogin(page, auth, gatewayUrl, clientId, waUser.username);

      const url = new URL(page.url());
      expect(url.searchParams.get('code')).toMatch(/^.+$/);
    });
  });

  test('AM-2379: passwordless login is blocked outside enforce password max age', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    test.setTimeout(120_000);
    const clientId = waApp.settings.oauth.clientId;

    // Register WebAuthn credential first (with enforce password disabled)
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Now enable enforce password with a max age of 1 second
    await waitForSyncAfter(waDomain.id, () =>
      patchDomain(waDomain.id, waAdminToken, {
        loginSettings: {
          inherited: false,
          passwordlessEnabled: true,
          passwordlessDeviceNamingEnabled: false,
          passwordlessEnforcePasswordEnabled: true,
          passwordlessEnforcePasswordMaxAge: 1,
        },
      }),
    );
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 15000, intervalMs: 300 });

    // Wait for the 1s max age to expire so the server enforces password re-auth
    await new Promise((r) => setTimeout(r, 2000));

    // Clear session and attempt passwordless login
    await page.context().clearCookies();

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await passwordlessLink.click();
    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    await page.locator('#username').fill(waUser.username);

    // The assertion succeeds client-side but the server rejects due to expired max age
    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    await page.waitForURL(/.*error=.*/i, { timeout: 15000 });

    const serverError = page.locator('.item.error-text:not(.hide) .error');
    await expect(serverError).toBeVisible({ timeout: 5000 });

    // Verify we did NOT get an authorization code
    expect(page.url()).not.toContain('callback?code=');
  });
});
