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
  VirtualAuthenticator,
} from '../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../utils/test-constants';
import { updateUserStatus } from '../../../api/commands/management/user-management-commands';

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
    }
  });

  test('disabled user cannot login via passwordless after registration', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Phase 1: Register WebAuthn credential via normal login flow
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Phase 2: Disable the user via management API
    await updateUserStatus(waDomain.id, waAdminToken, waUser.id, false);

    // Phase 3: Clear session and attempt passwordless login
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

    // Trigger the WebAuthn gesture. The virtual authenticator has the credential,
    // the JS will complete the assertion, but the server should reject because
    // the user is disabled. This means the form will POST, server rejects,
    // and redirects back with an error.
    await auth.cdpSession.send('WebAuthn.setUserVerified', {
      authenticatorId: auth.authenticatorId,
      isUserVerified: true,
    });
    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: true,
    });

    await page.locator('button.primary, button#login-button').click();

    // The server rejects the disabled user and redirects back to the webauthn login
    // page with an error parameter. Wait for the redirect to complete first,
    // then check the DOM — otherwise the execution context is destroyed mid-navigation.
    await page.waitForURL(/.*error=.*/i, { timeout: 15000 });

    const serverError = page.locator('.item.error-text:not(.hide) .error');
    await expect(serverError).toBeVisible({ timeout: 5000 });
    await expect(serverError).toContainText('login_failed');

    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: false,
    });
  });
});
