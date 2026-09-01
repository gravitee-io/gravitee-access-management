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
import { expect, Page } from '@playwright/test';
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
 * AM-2843 — conditional enrolment with a skip rule, alongside a conditional challenge.
 *
 * The enrolment settings are the same throughout and only the challenge rule differs, so the tests
 * read against each other. Two users enrol and then sign in again: the one under a rule that holds
 * is not challenged, the one under a rule that does not hold is, which is what shows the rule to be
 * live. A third user meets the same rule as the second but takes the skip instead of enrolling, and
 * goes through unchallenged — the skip, not the challenge setting, is what separates them.
 *
 * `MFAChallengeStep.conditional()` skips the challenge on three separate grounds: the rule holding,
 * a remembered device, or the user already being strongly authenticated. The last of those is the
 * reason the two enrolling tests clear the session cookie between visits — carrying the first
 * visit's session into the second would skip the challenge whatever the rule said, and both would
 * pass without the rule ever being consulted.
 */

/** One sign-in, reporting where the gateway stopped rather than assuming which page comes next. */
const signIn = async (page: Page, gatewayUrl: string, clientId: string, username: string) => {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);
  await submitLogin(page, username, API_USER_PASSWORD);
  await page.waitForURL((url) => /\/mfa\/(enroll|challenge)/i.test(url.href) || url.searchParams.has('code'));
  return {
    askedToEnrol: /\/mfa\/enroll/i.test(page.url()),
    challenged: /\/mfa\/challenge/i.test(page.url()),
  };
};

/** The enrolment settings the ticket describes; only the challenge rule differs between the tests. */
const conditionalEnrolmentWithSkip = {
  enrollActive: true,
  enrollType: 'CONDITIONAL' as const,
  // Force is on so the skip control is decided by the skip rule rather than short-circuited.
  enrollForce: true,
  // The rule does not hold, so the user is asked to enrol...
  enrollRule: '{{ false }}',
  // ...but the skip rule does, so a way past enrolment is offered.
  enrollSkipActive: true,
  enrollSkipRule: '{{ true }}',
  enrollSkipTimeSeconds: 3600,
  challengeActive: true,
  challengeType: 'CONDITIONAL' as const,
};

test.describe('Conditional enrolment with a skip rule and a conditional challenge that holds (AM-2843)', () => {
  test.use({ ...conditionalEnrolmentWithSkip, challengeRule: '{{ true }}' });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('an enrolled user is not challenged on a later sign-in while the rule holds', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2843');

    const clientId = matrixApp.settings.oauth.clientId;

    // First visit: the skip is offered, but this user enrols, then completes the verification the
    // gateway asks for straight after enrolling.
    const first = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(first.askedToEnrol).toBe(true);
    await enrollMockFactor(page);
    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);

    // Dropping the session leaves the user holding a method but no longer strongly authenticated,
    // so the challenge rule is what the second visit turns on.
    await clearSessionOnly(page);

    const second = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(second.askedToEnrol).toBe(false);
    expect(second.challenged).toBe(false);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

test.describe('Conditional enrolment with a skip rule and a conditional challenge that does not hold (AM-2843)', () => {
  test.use({ ...conditionalEnrolmentWithSkip, challengeRule: '{{ false }}' });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('the same user is challenged on a later sign-in once the rule no longer holds', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2843');

    const clientId = matrixApp.settings.oauth.clientId;

    // Identical first visit to the test above, under the same enrolment settings.
    const first = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(first.askedToEnrol).toBe(true);
    await enrollMockFactor(page);
    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);

    await clearSessionOnly(page);

    // Same point in the flow as the test above, and this time the rule sends them to the challenge.
    const second = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(second.askedToEnrol).toBe(false);
    expect(second.challenged).toBe(true);

    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('a user offered the skip is not challenged, though the rule would have challenged them', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2843');

    const clientId = matrixApp.settings.oauth.clientId;

    const first = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(first.askedToEnrol).toBe(true);

    // The enrolment rule did not hold, but the skip rule did, so a way past enrolment is offered.
    await expect(page.locator('button[name="user_mfa_enrollment"][value="false"]')).toBeVisible();
    await skipMfaEnrollment(page);

    // The user the test above enrolled was challenged under this same rule. This one skipped, so
    // `MFAEnrollPostEndpoint` marked the challenge complete alongside the skip and they go straight
    // through — the difference between the two is the enrolment, not the challenge setting.
    await reachOAuthAuthorizationCallback(page);
    expect(page.url()).not.toMatch(/mfa\/challenge/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
