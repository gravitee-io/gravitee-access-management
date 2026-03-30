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
  buildAuthorizeUrl,
  submitLogin,
  enrollMockFactor,
  completeMfaChallenge,
  handleConsentIfPresent,
  fullMfaLogin,
  MOCK_MFA_CODE,
} from '../../fixtures/mfa-login.fixture';
import { linkJira } from '../../utils/jira';
import { AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT, API_USER_PASSWORD } from '../../utils/test-constants';

// Gateway auth flow tests need clean browser state
test.use({ storageState: { cookies: [], origins: [] } });

/* ------------------------------------------------------------------ */
/*  AM-2197: Single MFA method during sign-in                          */
/* ------------------------------------------------------------------ */

test.describe('Single MFA login (AM-2197)', () => {
  // Default: 1 factor, force enrollment
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user enrolls in single MFA factor and completes challenge', async ({ page, gatewayUrl, mfaApp, mfaUser }, testInfo) => {
    linkJira(testInfo, 'AM-2197');

    // Navigate to authorize endpoint — redirects to login
    await page.goto(buildAuthorizeUrl(gatewayUrl, mfaApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    // Submit login credentials
    await submitLogin(page, mfaUser.username, API_USER_PASSWORD);

    // Redirects to MFA enrollment — only one factor available
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    // Single factor: only one radio button
    const radios = page.locator('input[type="radio"][name="factorId"]');
    await expect(radios).toHaveCount(1);

    // Enroll in the mock factor
    await enrollMockFactor(page);

    // Redirects to MFA challenge (completeMfaChallenge anchors #code)
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    // Handle consent if present, then verify callback with auth code
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2191: Multiple MFA methods during sign-in                       */
/* ------------------------------------------------------------------ */

test.describe('Multiple MFA login (AM-2191)', () => {
  test.use({ factorCount: 2 });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user sees multiple factors on enrollment page and selects one', async ({ page, gatewayUrl, mfaApp, mfaUser }, testInfo) => {
    linkJira(testInfo, 'AM-2191');

    await page.goto(buildAuthorizeUrl(gatewayUrl, mfaApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, mfaUser.username, API_USER_PASSWORD);

    // Redirects to MFA enrollment — two factors available
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    const radios = page.locator('input[type="radio"][name="factorId"]');
    await expect(radios).toHaveCount(2);

    // Select the second factor (index 1) to verify choice works
    await enrollMockFactor(page, 1);

    // Complete MFA challenge
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2221: Force MFA enrollment during sign-in                       */
/* ------------------------------------------------------------------ */

test.describe('Force MFA enrollment (AM-2221)', () => {
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('skip button is absent when enrollment is forced', async ({ page, gatewayUrl, mfaApp, mfaUser }, testInfo) => {
    linkJira(testInfo, 'AM-2221');

    await page.goto(buildAuthorizeUrl(gatewayUrl, mfaApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, mfaUser.username, API_USER_PASSWORD);

    // Enrollment page — forced enrollment means no skip button
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    // The skip button (user_mfa_enrollment=false) should NOT be present
    // Positive anchor: step1 is visible, proving the page loaded
    const skipButton = page.locator('button[name="user_mfa_enrollment"][value="false"]');
    await expect(skipButton).toHaveCount(0);

    // Complete the full flow to verify it works end-to-end
    await enrollMockFactor(page);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('user completes forced enrollment and authentication end-to-end', async ({ page, gatewayUrl, mfaApp, mfaUser }, testInfo) => {
    linkJira(testInfo, 'AM-2221');

    // Use the composite helper for a concise end-to-end test
    await fullMfaLogin(page, gatewayUrl, mfaApp.settings.oauth.clientId, mfaUser.username, API_USER_PASSWORD);

    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
