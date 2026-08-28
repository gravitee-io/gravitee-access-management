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
import { expect } from '@playwright/test';
import { test } from '../../fixtures/mfa-enrollment-matrix.fixture';
import { linkJira } from '../../utils/jira';
import { clearSessionOnly } from '../../utils/webauthn-helpers';
import {
  buildAuthorizeUrl,
  completeMfaChallenge,
  enrollMockFactor,
  reachOAuthAuthorizationCallback,
  skipMfaEnrollment,
  submitLogin,
} from '../../utils/mfa-helpers';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

/**
 * AM-2842 — conditional enrolment with a skip rule, alongside a required challenge.
 *
 * The two tests are each other's control. Both applications are configured identically and both
 * users sign in twice; the only difference is whether the first visit ended in enrolling or in
 * skipping. One is then challenged and the other is not, so the challenge setting is shown to be
 * live and the skip is shown to be what suppresses it.
 *
 * `MFAEnrollStep.conditional()` reaches the challenge through `userHasFactor -> continueFlow` when
 * the user already holds a method. Skipping instead goes through `MFAEnrollPostEndpoint`, which
 * marks the challenge complete for that authorisation leg at the same time as recording the skip.
 */
test.describe('Conditional enrolment with a skip rule and a required challenge (AM-2842)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    // Force is on so the skip control is decided by the skip rule rather than short-circuited.
    enrollForce: true,
    // The rule does not hold, so the user is asked to enrol...
    enrollRule: '{{ false }}',
    // ...but the skip rule does, so they are offered a way past it.
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    enrollSkipTimeSeconds: 3600,
    challengeActive: true,
    challengeType: 'REQUIRED',
    challengeRule: '',
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  const signIn = async (page, gatewayUrl: string, clientId: string, username: string) => {
    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, username, API_USER_PASSWORD);
    await page.waitForURL(
      (url) => /\/mfa\/(enroll|challenge)/i.test(url.href) || url.searchParams.has('code'),
    );
    return {
      askedToEnrol: /\/mfa\/enroll/i.test(page.url()),
      challenged: /\/mfa\/challenge/i.test(page.url()),
    };
  };

  test('a user who enrolled is asked for a code on a later sign-in', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2842');

    const clientId = matrixApp.settings.oauth.clientId;

    // First visit: the skip is offered, but this user enrols instead.
    const first = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(first.askedToEnrol).toBe(true);
    await enrollMockFactor(page);
    // The verification the gateway always asks for straight after enrolling.
    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);

    // Second visit: they hold a method now, so enrolment is behind them and the required
    // challenge is what they meet.
    await clearSessionOnly(page);
    const second = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(second.askedToEnrol).toBe(false);
    expect(second.challenged).toBe(true);

    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('a user who skipped has no method, so the required challenge never applies', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2842');

    const clientId = matrixApp.settings.oauth.clientId;

    // Same application and the same required challenge as the test above — this user takes the
    // skip rather than enrolling.
    const first = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(first.askedToEnrol).toBe(true);

    // The skip rule holds, so the way past enrolment is offered even though the rule requiring
    // enrolment did not. Asserted here rather than left to the helper.
    await expect(page.locator('button[name="user_mfa_enrollment"][value="false"]')).toBeVisible();

    await skipMfaEnrollment(page);
    await reachOAuthAuthorizationCallback(page);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);

    // Second visit, still inside the skip window: no method to be challenged for, and the skip
    // still standing, so they are asked for neither.
    await clearSessionOnly(page);
    const second = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(second.askedToEnrol).toBe(false);
    expect(second.challenged).toBe(false);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
