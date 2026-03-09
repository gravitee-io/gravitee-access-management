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
  removeVirtualAuthenticator,
  buildAuthorizeUrl,
  PASSWORDLESS_LINK_SELECTOR,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../../utils/test-constants';
import { updateUserStatus } from '../../../../api/commands/management/user-management-commands';

/**
 * AM-4553: Authentication filter - With webauthn registered user (Unsuccessful attempts)
 *
 * Verifies that a user who has registered a WebAuthn credential cannot
 * use passwordless login after their account is disabled.
 */
test.describe('WebAuthn - Disabled User (AM-4553)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('AM-4553: disabled user cannot login via passwordless after registration', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    test.setTimeout(120_000);
    const clientId = waApp.settings.oauth.clientId;

    // Phase 1: Register WebAuthn credential via normal login flow
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Phase 2: Disable the user via management API
    await updateUserStatus(waDomain.id, waAdminToken, waUser.id, false);

    // Phase 3: Clear session and attempt passwordless login
    await page.context().clearCookies();

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await passwordlessLink.click();
    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    await page.locator('#username').fill(waUser.username);

    // The virtual authenticator completes the assertion (credentialAsserted fires),
    // but the server rejects because the user is disabled and redirects with error.
    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    await page.waitForURL(/.*error=.*/i, { timeout: 15000 });

    const serverError = page.locator('.item.error-text:not(.hide) .error');
    await expect(serverError).toBeVisible({ timeout: 5000 });
    await expect(serverError).toContainText('login_failed');
  });
});
