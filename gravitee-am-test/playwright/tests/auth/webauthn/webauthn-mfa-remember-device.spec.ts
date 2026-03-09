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
import { test, expect, MOCK_MFA_CODE } from '../../../fixtures/webauthn-mfa.fixture';
import {
  fullLoginWithMfaAndWebAuthn,
  simulateWebAuthnGesture,
  handleConsentIfPresent,
  removeVirtualAuthenticator,
  clearSessionOnly,
  navigateToWebAuthnLogin,
  buildAuthorizeUrl,
  PASSWORDLESS_LINK_SELECTOR,
  VirtualAuthenticator,
} from '../../../utils/webauthn-helpers';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
import { linkJira } from '../../../utils/jira';

/**
 * AM-5333: MFA when Remember Device enabled - Passwordless with Device NOT remembered
 * AM-5334: MFA when Remember Device enabled - Passwordless with Device remembered
 */
test.describe('WebAuthn - MFA + Remember Device (AM-5333, AM-5334)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('AM-5334: passwordless login skips MFA when device is remembered', async ({
    page,
    mfaApp,
    mfaUser,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-5334');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);
    const clientId = mfaApp.settings.oauth.clientId;

    // First login: full flow with MFA enrollment + WebAuthn + remember device checked
    auth = await fullLoginWithMfaAndWebAuthn(page, gatewayUrl, clientId, mfaUser.username, API_USER_PASSWORD, MOCK_MFA_CODE, true);

    // Clear session only — keep device recognition + remember device cookies
    await clearSessionOnly(page);

    // Second login: passwordless — should skip MFA because device is remembered
    await navigateToWebAuthnLogin(page, gatewayUrl, clientId);

    await page.locator('#username').fill(mfaUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Should go straight to consent/callback — no MFA challenge
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('AM-5333: passwordless login requires MFA when device is NOT remembered', async ({
    page,
    mfaApp,
    mfaUser,
    gatewayUrl,
  }, testInfo) => {
    linkJira(testInfo, 'AM-5333');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);
    const clientId = mfaApp.settings.oauth.clientId;

    // First login: full flow with MFA enrollment + WebAuthn but WITHOUT checking remember device
    auth = await fullLoginWithMfaAndWebAuthn(page, gatewayUrl, clientId, mfaUser.username, API_USER_PASSWORD, MOCK_MFA_CODE, false);

    // Clear ALL cookies — no device remembered
    await page.context().clearCookies();

    // Second login: passwordless — should still need MFA challenge
    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);

    // Navigate to passwordless login
    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await passwordlessLink.click();
    await page.waitForURL(/.*webauthn\/login.*/i);

    await page.locator('#username').fill(mfaUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Should be challenged with MFA (device not remembered)
    await page.waitForURL(/.*mfa\/challenge.*/i);

    // Enter the mock MFA code
    await page.locator('#code').fill(MOCK_MFA_CODE);
    await page.locator('#verify').click();

    // Now should proceed to consent/callback
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
