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
} from '../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../utils/test-constants';
import { updateUsername, listUserCredentials } from '../../../api/commands/management/user-management-commands';
import { uniqueTestName } from '../../utils/fixture-helpers';

/**
 * Poll credentials via management API until the username field matches the expected value.
 * The credential username update is fire-and-forget on the server side, so we need to wait.
 */
async function waitForCredentialUsernameUpdate(
  domainId: string,
  accessToken: string,
  userId: string,
  expectedUsername: string,
  timeoutMs = 10000,
): Promise<void> {
  const start = Date.now();
  const expected = expectedUsername.toLowerCase();
  while (Date.now() - start < timeoutMs) {
    const creds = await listUserCredentials(domainId, accessToken, userId);
    if (creds.length > 0 && creds.every((c) => c.username?.toLowerCase() === expected)) {
      return;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Credential username not updated to "${expectedUsername}" within ${timeoutMs}ms`);
}

/**
 * AM-2342: Change Username - MFA - FIDO2 Factor (Webauthn)
 *
 * Verifies that after a user's username is changed via the management API,
 * their WebAuthn credentials still work for passwordless login with the
 * new username. The server updates credential usernames asynchronously
 * (fire-and-forget) when UpdateUsernameDomainRule runs.
 */
test.describe('WebAuthn - Change Username (AM-2342)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('passwordless login works after username change (AM-2342)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Phase 1: Register a WebAuthn credential with the original username
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Phase 2: Change the user's username via management API
    const newUsername = uniqueTestName('wa-renamed');
    await updateUsername(waDomain.id, waAdminToken, waUser.id, newUsername);

    // Credential username update is fire-and-forget on the server side —
    // poll until the management API confirms the credential has the new username.
    await waitForCredentialUsernameUpdate(waDomain.id, waAdminToken, waUser.id, newUsername);

    // The IDP lowercases the username, so the credential and user both store it lowercased.
    const newUsernameLower = newUsername.toLowerCase();

    // Phase 3: Clear session and attempt passwordless login with the NEW username
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

    // Use the NEW username (lowercased, as the IDP normalizes it)
    await page.locator('#username').fill(newUsernameLower);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toBeTruthy();
  });

  test('passwordless login fails with OLD username after change (AM-2342)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;
    const originalUsername = waUser.username;

    // Phase 1: Register a WebAuthn credential
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, originalUsername, API_USER_PASSWORD);

    // Phase 2: Change the user's username
    const newUsername = uniqueTestName('wa-renamed2');
    await updateUsername(waDomain.id, waAdminToken, waUser.id, newUsername);

    // Wait for credential username to be updated (fire-and-forget on server)
    await waitForCredentialUsernameUpdate(waDomain.id, waAdminToken, waUser.id, newUsername);

    // Phase 3: Attempt passwordless login with the OLD username — should fail
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

    // Use the OLD username — should fail since credentials were updated to new username
    await page.locator('#username').fill(originalUsername);

    await auth.cdpSession.send('WebAuthn.setUserVerified', {
      authenticatorId: auth.authenticatorId,
      isUserVerified: true,
    });
    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: true,
    });

    await page.locator('button.primary, button#login-button').click();

    // The server won't find credentials for the old username (they were updated).
    // navigator.credentials.get() will fail, and the JS sets style.display='block'
    // on #webauthn-error (note: the .hide class is NOT removed, inline style overrides it).
    await expect(page.locator('#webauthn-error')).toBeVisible({ timeout: 30000 });

    // Verify we did NOT get an authorization code
    expect(page.url()).not.toContain('callback?code=');

    await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
      authenticatorId: auth.authenticatorId,
      enabled: false,
    });
  });
});
