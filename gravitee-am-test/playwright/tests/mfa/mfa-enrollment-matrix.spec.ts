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
  skipMfaEnrollment,
  secondAuthorizeExpectCallbackWithoutMfa,
  waitAfterAuthorizeThenLoginIfNeededAllowMfa,
  MOCK_MFA_CODE,
} from '../../fixtures/mfa-enrollment-matrix.fixture';
import { linkJira } from '../../utils/jira';
import { clearSessionOnly } from '../../utils/webauthn-helpers';
import { AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT, API_USER_PASSWORD } from '../../utils/test-constants';
import { waitUntilMfaEnrollmentSkipWindowExpired, applyStandaloneChallengeMfaPatch } from '../../utils/mfa-helpers';

// Gateway auth flow tests need clean browser state
test.use({ storageState: { cookies: [], origins: [] } });

/* ------------------------------------------------------------------ */
/*  AM-2822: Enrollment Required                                       */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Required (AM-2822)', () => {
  test.use({ enrollActive: true, enrollType: 'REQUIRED', enrollForce: true, challengeActive: false });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user must enroll when enrollment is required, no skip button', async ({ page, gatewayUrl, matrixApp, matrixUser }, testInfo) => {
    linkJira(testInfo, 'AM-2822');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    // Enrollment page — required means no skip button
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    const skipButton = page.locator('button[name="user_mfa_enrollment"][value="false"]');
    await expect(skipButton).toHaveCount(0);

    // Complete enrollment — gateway always verifies the factor immediately after enrollment
    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2821: Enrollment Optional — user skips                          */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Optional — skip (AM-2821)', () => {
  test.use({ enrollActive: true, enrollType: 'OPTIONAL', enrollForce: false, challengeActive: false });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user sees skip button and skips enrollment', async ({ page, gatewayUrl, matrixApp, matrixUser }, testInfo) => {
    linkJira(testInfo, 'AM-2821');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    // Enrollment page — optional means skip button is visible
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    await skipMfaEnrollment(page);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2190: Minimal skip window — next login re-prompts enrollment    */
/*  (Phase 7 login flows; reuses matrix fixture.)                      */
/* ------------------------------------------------------------------ */

test.describe('Enrollment optional — minimal skip window re-prompt (AM-2190)', () => {
  /**
   * {@code skipTimeSeconds: 0} cannot complete the first OAuth leg: {@code MfaFilterContext.isEnrollSkipped()}
   * uses {@code skippedAt + skipTime > now}, so a zero window is never true and optional enrollment loops on
   * {@code /mfa/enroll}. A small positive window lets the first skip reach the callback; we then wait until the
   * Management API shows the skip window has elapsed (no fixed wall-clock sleep).
   */
  const enrollSkipTimeSeconds = 3;

  test.use({
    enrollActive: true,
    enrollType: 'OPTIONAL',
    enrollForce: false,
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    enrollSkipTimeSeconds,
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('AM-2190: after skipping enrollment, next login shows enrollment when skip window has elapsed', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
    matrixDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2190');

    const clientId = matrixApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await skipMfaEnrollment(page);
    const skipHandledAtMs = Date.now();

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);

    await waitUntilMfaEnrollmentSkipWindowExpired(
      matrixDomain.id,
      adminToken,
      matrixUser.id,
      enrollSkipTimeSeconds,
      skipHandledAtMs,
    );

    await clearSessionOnly(page);

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2825: Optional enrollment + skip window (skipTimeSeconds)       */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Optional with enrollment skip window (AM-2825)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'OPTIONAL',
    enrollForce: false,
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    enrollSkipTimeSeconds: 3600,
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user skips optional enrollment when skip policy and duration are configured', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2825');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await skipMfaEnrollment(page);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2827: Optional enrollment + longer skip window                  */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Optional with extended enrollment skip window (AM-2827)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'OPTIONAL',
    enrollForce: false,
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    enrollSkipTimeSeconds: 86400,
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user skips optional enrollment with 24h skip window configured', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2827');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await skipMfaEnrollment(page);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2823: Enrollment Conditional — rule evaluates to true           */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Conditional = true (AM-2823)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ true }}',
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('enrollment is skipped when conditional rule evaluates to true', async ({ page, gatewayUrl, matrixApp, matrixUser }, testInfo) => {
    linkJira(testInfo, 'AM-2823');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    // Rule = true → enrollment is bypassed. Flow goes to consent/callback.
    // Reaching callback proves enrollment was skipped — if enrollment had appeared,
    // the test would timeout because enrollment requires user interaction.
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
    // Confirm current URL is NOT the enrollment page
    expect(page.url()).not.toMatch(/mfa\/enroll/);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2824: Enrollment Conditional — rule evaluates to false          */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Conditional = false (AM-2824)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: false,
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('enrollment page is shown when conditional rule evaluates to false', async ({ page, gatewayUrl, matrixApp, matrixUser }, testInfo) => {
    linkJira(testInfo, 'AM-2824');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    // Rule = false → enrollment is required
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    await enrollMockFactor(page);

    // Gateway always verifies the factor immediately after enrollment
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2826: Conditional enrollment + skip disabled + REQUIRED challenge */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Conditional false + skip disabled + REQUIRED challenge (AM-2826)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: false,
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user enrolls when conditional rule is false then completes required challenge', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2826');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2840: Conditional enrollment false + CONDITIONAL challenge      */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Conditional false + CONDITIONAL challenge (AM-2840)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: false,
    challengeActive: true,
    challengeType: 'CONDITIONAL',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('enrollment is shown then post-enroll verification completes with conditional challenge enabled', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2840');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2841: Conditional false + enrollment skip + RISK_BASED challenge */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Conditional false + skip policy + RISK_BASED challenge (AM-2841)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    challengeActive: true,
    challengeType: 'RISK_BASED',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user enrolls then post-enroll flow completes with risk-based challenge rule safe', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2841');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2842: Conditional false + enrollment skip + REQUIRED challenge   */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Conditional false + skip policy + REQUIRED challenge (AM-2842)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user enrolls then completes required challenge with enrollment skip policy enabled', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2842');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2843: Conditional false + enrollment skip + CONDITIONAL challenge */
/* ------------------------------------------------------------------ */

test.describe('Enrollment Conditional false + skip policy + CONDITIONAL challenge (AM-2843)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    challengeActive: true,
    challengeType: 'CONDITIONAL',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user enrolls then post-enroll verification with conditional challenge enabled', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2843');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2836: Required Enrollment + Required Challenge                  */
/* ------------------------------------------------------------------ */

test.describe('Required Enrollment + Required Challenge (AM-2836)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'REQUIRED',
    enrollForce: true,
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user enrolls then completes MFA challenge', async ({ page, gatewayUrl, matrixApp, matrixUser }, testInfo) => {
    linkJira(testInfo, 'AM-2836');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    // Phase 1: enrollment
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);

    // Phase 2: challenge (completeMfaChallenge anchors #code)
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2833: Optional Enrollment + Required Challenge                  */
/* ------------------------------------------------------------------ */

test.describe('Optional Enrollment + Required Challenge (AM-2833)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'OPTIONAL',
    enrollForce: false,
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user enrolls via optional enrollment then faces required challenge', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2833');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);

    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    // Enrollment page — optional, skip button should be visible
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();
    const skipButton = page.locator('button[name="user_mfa_enrollment"][value="false"]');
    await expect(skipButton).toBeVisible();

    // User chooses to enroll (not skip)
    await enrollMockFactor(page);

    // Challenge follows enrollment (completeMfaChallenge anchors #code)
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2832: Optional Enrollment + RISK_BASED challenge (rule = safe) */
/* ------------------------------------------------------------------ */

test.describe('Optional Enrollment + RISK_BASED challenge skips MFA when rule is safe (AM-2832)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'OPTIONAL',
    enrollForce: false,
    challengeActive: true,
    challengeType: 'RISK_BASED',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user skips optional enrollment; risk-based challenge evaluates safe and is skipped', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2832');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await skipMfaEnrollment(page);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(page.url()).not.toMatch(/mfa\/challenge/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2834: Optional Enrollment + CONDITIONAL challenge (rule true)  */
/* ------------------------------------------------------------------ */

test.describe('Optional Enrollment + CONDITIONAL challenge skips MFA when rule is true (AM-2834)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'OPTIONAL',
    enrollForce: false,
    challengeActive: true,
    challengeType: 'CONDITIONAL',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('user skips optional enrollment; conditional challenge rule true skips challenge', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2834');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await skipMfaEnrollment(page);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(page.url()).not.toMatch(/mfa\/challenge/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2838: Conditional enrollment (true) + RISK_BASED challenge (safe) */
/* ------------------------------------------------------------------ */

test.describe('Conditional enrollment true + RISK_BASED challenge safe (AM-2838)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ true }}',
    challengeActive: true,
    challengeType: 'RISK_BASED',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('enrollment bypassed by conditional rule; risk-based challenge evaluates safe', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2838');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(page.url()).not.toMatch(/mfa\/enroll/i);
    expect(page.url()).not.toMatch(/mfa\/challenge/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2839: Conditional enrollment true + REQUIRED challenge active     */
/* ------------------------------------------------------------------ */

test.describe('Conditional enrollment true + REQUIRED challenge active (AM-2839)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ true }}',
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('enrollment conditional bypasses MFA enrolment; gateway completes without MFA pages', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2839');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(page.url()).not.toMatch(/mfa\/enroll/i);
    expect(page.url()).not.toMatch(/mfa\/challenge/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2835: Required Enrollment + RISK_BASED — second login skips MFA    */
/* ------------------------------------------------------------------ */

test.describe('Required Enrollment + RISK_BASED challenge (AM-2835)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'REQUIRED',
    enrollForce: true,
    challengeActive: true,
    challengeType: 'RISK_BASED',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('first login completes enroll+verify; second authorisation skips challenge when risk rule is safe', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2835');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const firstCode = new URL(page.url()).searchParams.get('code');
    expect(firstCode).toMatch(AUTH_CODE_FORMAT);

    await secondAuthorizeExpectCallbackWithoutMfa(
      page,
      gatewayUrl,
      matrixApp.settings.oauth.clientId,
      matrixUser.username,
      API_USER_PASSWORD,
    );
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2837: Required Enrollment + CONDITIONAL — second login skips MFA */
/* ------------------------------------------------------------------ */

test.describe('Required Enrollment + CONDITIONAL challenge (AM-2837)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'REQUIRED',
    enrollForce: true,
    challengeActive: true,
    challengeType: 'CONDITIONAL',
    challengeRule: '{{ true }}',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('first login completes enroll+verify; second authorisation skips challenge when condition is true', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2837');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const firstCode = new URL(page.url()).searchParams.get('code');
    expect(firstCode).toMatch(AUTH_CODE_FORMAT);

    await secondAuthorizeExpectCallbackWithoutMfa(
      page,
      gatewayUrl,
      matrixApp.settings.oauth.clientId,
      matrixUser.username,
      API_USER_PASSWORD,
    );
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2828–AM-2831: Standalone challenge (enroll off after bootstrap)   */
/* ------------------------------------------------------------------ */

test.describe('Standalone challenge RISK_BASED rule safe (AM-2828)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'REQUIRED',
    enrollForce: true,
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('bootstrap enroll then patch enroll off; second OAuth skips challenge when risk rule is safe', async ({
    page,
    adminToken,
    matrixDomain,
    matrixApp,
    matrixUser,
    gatewayUrl,
    factorId,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2828');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);

    await applyStandaloneChallengeMfaPatch(matrixDomain.id, adminToken, matrixApp.id, factorId, {
      active: true,
      type: 'RISK_BASED',
      challengeRule: '{{ true }}',
    }, matrixDomain.hrid);

    await secondAuthorizeExpectCallbackWithoutMfa(
      page,
      gatewayUrl,
      matrixApp.settings.oauth.clientId,
      matrixUser.username,
      API_USER_PASSWORD,
    );
    expect(page.url()).toMatch(/callback\?code=/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

test.describe('Standalone challenge REQUIRED (AM-2829)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'REQUIRED',
    enrollForce: true,
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('bootstrap enroll then patch enroll off; second OAuth requires MFA challenge', async ({
    page,
    adminToken,
    matrixDomain,
    matrixApp,
    matrixUser,
    gatewayUrl,
    factorId,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2829');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);

    await applyStandaloneChallengeMfaPatch(matrixDomain.id, adminToken, matrixApp.id, factorId, {
      active: true,
      type: 'REQUIRED',
      challengeRule: '',
    }, matrixDomain.hrid);

    // Drop AM session so the second authorisation cannot short-circuit to callback without step-up MFA.
    await page.context().clearCookies();

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await waitAfterAuthorizeThenLoginIfNeededAllowMfa(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

test.describe('Standalone challenge CONDITIONAL rule true skips challenge (AM-2830)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'REQUIRED',
    enrollForce: true,
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('bootstrap enroll then patch enroll off; second OAuth skips challenge when condition is true', async ({
    page,
    adminToken,
    matrixDomain,
    matrixApp,
    matrixUser,
    gatewayUrl,
    factorId,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2830');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);

    await applyStandaloneChallengeMfaPatch(matrixDomain.id, adminToken, matrixApp.id, factorId, {
      active: true,
      type: 'CONDITIONAL',
      challengeRule: '{{ true }}',
    }, matrixDomain.hrid);

    await secondAuthorizeExpectCallbackWithoutMfa(
      page,
      gatewayUrl,
      matrixApp.settings.oauth.clientId,
      matrixUser.username,
      API_USER_PASSWORD,
    );
    expect(page.url()).toMatch(/callback\?code=/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

test.describe('Standalone challenge CONDITIONAL rule false requires challenge (AM-2831)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'REQUIRED',
    enrollForce: true,
    challengeActive: true,
    challengeType: 'REQUIRED',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('bootstrap enroll then patch enroll off; second OAuth runs MFA challenge when condition is false', async ({
    page,
    adminToken,
    matrixDomain,
    matrixApp,
    matrixUser,
    gatewayUrl,
    factorId,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2831');

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);

    await applyStandaloneChallengeMfaPatch(matrixDomain.id, adminToken, matrixApp.id, factorId, {
      active: true,
      type: 'CONDITIONAL',
      challengeRule: '{{ false }}',
    }, matrixDomain.hrid);

    await page.context().clearCookies();

    await page.goto(buildAuthorizeUrl(gatewayUrl, matrixApp.settings.oauth.clientId));
    await waitAfterAuthorizeThenLoginIfNeededAllowMfa(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
