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
  navigateToWebAuthnLogin,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT, BRIEF_TIMEOUT } from '../../../utils/test-constants';
import { patchDomain, waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { waitForSyncAfter } from '../../../../api/commands/gateway/monitoring-commands';
import { linkJira } from '../../../utils/jira';

/**
 * AM-2370: Enforce Password + Device Recognition - Within usage limit
 * AM-2374: Enforce Password + Device Recognition - Idle
 * AM-2382: Enforce Password + Device Recognition - Outside usage limit
 *
 * When both enforce password usage and device recognition are enabled,
 * the system should respect the max age for password re-authentication
 * while still routing returning users to the WebAuthn login page.
 */
test.describe('WebAuthn - Enforce Password + Device Recognition', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test.describe('with enforce password + device recognition pre-configured', () => {
    test.use({
      waExtraLoginSettings: {
        passwordlessRememberDeviceEnabled: true,
        passwordlessEnforcePasswordEnabled: true,
        passwordlessEnforcePasswordMaxAge: 3600,
      },
    });

    test('AM-2370: passwordless login succeeds within enforce password max age with device recognition', async ({
      page,
      waApp,
      waUser,
      gatewayUrl,
    }, testInfo) => {
      linkJira(testInfo, 'AM-2370');
      const clientId = waApp.settings.oauth.clientId;

      // Enforce password + device recognition configured via waExtraLoginSettings before domain start.
      // Register WebAuthn credential via password login.
      auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

      // Clear session only — keep device recognition cookie
      await clearSessionOnly(page);

      await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));

      // Device recognition should route us directly to WebAuthn login page
      await page.waitForURL(/.*webauthn\/login.*/i);

      await page.locator('#username').fill(waUser.username);

      await simulateWebAuthnGesture(auth, async () => {
        await page.locator('button.primary, button#login-button').click();
      });

      // Within max age — passwordless should succeed
      await handleConsentIfPresent(page);
      await page.waitForURL(/.*callback\?code=.*/i);

      const url = new URL(page.url());
      expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
    });
  });

  test('AM-2382: passwordless login is blocked outside enforce password max age with device recognition', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2382');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);
    const clientId = waApp.settings.oauth.clientId;

    // Register credential first (without enforce password)
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Now enable enforce password + device recognition with 1s max age
    await waitForSyncAfter(waDomain.id, () =>
      patchDomain(waDomain.id, waAdminToken, {
        loginSettings: {
          inherited: false,
          passwordlessEnabled: true,
          passwordlessDeviceNamingEnabled: false,
          passwordlessRememberDeviceEnabled: true,
          passwordlessEnforcePasswordEnabled: true,
          passwordlessEnforcePasswordMaxAge: 1,
        },
      }),
    );
    await waitForOidcReady(waDomain.hrid);

    // Wait for the 1s max age to expire so the server enforces password re-auth
    // eslint-disable-next-line playwright/no-wait-for-timeout
    await page.waitForTimeout(2000);

    // Clear session only — keep device recognition cookie
    await clearSessionOnly(page);

    await navigateToWebAuthnLogin(page, gatewayUrl, clientId);

    await page.locator('#username').fill(waUser.username);

    // The assertion succeeds client-side but the server rejects due to expired max age
    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Outside max age — server should reject and redirect with error
    await page.waitForURL(/.*error=.*/i);

    const serverError = page.locator('.item.error-text:not(.hide) .error');
    await expect(serverError).toBeVisible({ timeout: BRIEF_TIMEOUT });

    expect(page.url()).not.toContain('callback?code=');
  });
});
