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
import { createTestApp } from '@utils-commands/application-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { uniqueTestName, quietly } from '../../utils/fixture-helpers';
import {
  REDIRECT_URI,
  buildAuthorizeUrl,
  completeMfaChallenge,
  enrollMockFactor,
  reachOAuthAuthorizationCallback,
  skipMfaEnrollment,
  submitLogin,
  waitUntilMfaEnrollmentSkipWindowExpired,
} from '../../utils/mfa-helpers';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

/**
 * AM-2825 / AM-2826 / AM-2827 — the skip rule layered on conditional enrolment.
 *
 * `MFAEnrollStep.conditional()` takes the skip branch only when `enrollmentSkipActive` is on and
 * the skip rule holds, and records that in the session. The enrolment page then renders its skip
 * control from `!MfaUtils.isCanSkip(...)`, which reads that flag.
 *
 * `forceEnrollment` is deliberately **on** throughout. With it off, `isCanSkip` short-circuits on
 * `isNotForcedEnrollment` and the skip control appears whatever the rule says — so the rule would
 * not be the thing under test. The presence and absence of that control are asserted against each
 * other, since a page without a skip button looks the same whether the rule was evaluated or never
 * consulted.
 */
const SHORT_WINDOW_SECONDS = 3;
const LONG_WINDOW_SECONDS = 3600;

const skipButton = (page) => page.locator('button[name="user_mfa_enrollment"][value="false"]');

/** Signs in as far as the enrolment page, or to the callback if enrolment was not asked for. */
const signIn = async (page, gatewayUrl: string, clientId: string, username: string) => {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);
  await submitLogin(page, username, API_USER_PASSWORD);
  await page.waitForURL((url) => /\/mfa\/enroll/i.test(url.href) || url.searchParams.has('code'));
  return { askedToEnrol: /\/mfa\/enroll/i.test(page.url()) };
};

/* ------------------------------------------------------------------ */
/*  AM-2825 — the skip rule holds                                      */
/* ------------------------------------------------------------------ */

test.describe('Conditional enrolment where the skip rule holds (AM-2825)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    enrollSkipTimeSeconds: SHORT_WINDOW_SECONDS,
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('the user is offered the chance to skip, and skipping completes the sign-in', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2825');

    const asked = await signIn(page, gatewayUrl, matrixApp.settings.oauth.clientId, matrixUser.username);
    expect(asked.askedToEnrol).toBe(true);

    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();
    await expect(skipButton(page)).toBeVisible();

    await skipMfaEnrollment(page);
    await reachOAuthAuthorizationCallback(page);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('declining the offered skip takes the user through enrolment instead', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2825');

    await signIn(page, gatewayUrl, matrixApp.settings.oauth.clientId, matrixUser.username);

    // The button is there to be declined, which is what separates this from an enrolment where
    // no choice was ever offered.
    await expect(skipButton(page)).toBeVisible();

    await enrollMockFactor(page);
    // Enrolling a factor is always verified, whatever the challenge setting says.
    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('a user who has already enrolled is let straight through', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2825');

    const clientId = matrixApp.settings.oauth.clientId;

    await signIn(page, gatewayUrl, clientId, matrixUser.username);
    await enrollMockFactor(page);
    // Enrolling a factor is always verified, whatever the challenge setting says.
    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);

    // Second sign-in: the factor is already held, so enrolment is not revisited.
    await clearSessionOnly(page);
    const second = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(second.askedToEnrol).toBe(false);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  test('the user is left alone inside the skip window and asked again once it passes', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
    matrixDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2825');

    const clientId = matrixApp.settings.oauth.clientId;

    await signIn(page, gatewayUrl, clientId, matrixUser.username);
    await skipMfaEnrollment(page);
    const skippedAtMs = Date.now();
    await reachOAuthAuthorizationCallback(page);

    await clearSessionOnly(page);
    const inside = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(inside.askedToEnrol).toBe(false);

    await waitUntilMfaEnrollmentSkipWindowExpired(matrixDomain.id, adminToken, matrixUser.id, SHORT_WINDOW_SECONDS, skippedAtMs);

    await clearSessionOnly(page);
    const after = await signIn(page, gatewayUrl, clientId, matrixUser.username);
    expect(after.askedToEnrol).toBe(true);
    await expect(skipButton(page)).toBeVisible();
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2826 — the skip rule does not hold                              */
/* ------------------------------------------------------------------ */

test.describe('Conditional enrolment where the skip rule does not hold (AM-2826)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    // Skipping is switched on, so the rule is genuinely evaluated rather than bypassed.
    enrollSkipActive: true,
    enrollSkipRule: '{{ false }}',
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('no skip is offered and the user must enrol', async ({ page, gatewayUrl, matrixApp, matrixUser }, testInfo) => {
    linkJira(testInfo, 'AM-2826');

    const asked = await signIn(page, gatewayUrl, matrixApp.settings.oauth.clientId, matrixUser.username);
    expect(asked.askedToEnrol).toBe(true);

    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();
    // The counterpart of the AM-2825 assertion: same mode, same page, opposite rule.
    await expect(skipButton(page)).toHaveCount(0);

    await enrollMockFactor(page);
    // Enrolling a factor is always verified, whatever the challenge setting says.
    await completeMfaChallenge(page);
    await reachOAuthAuthorizationCallback(page);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-2827 — altering the skip duration                               */
/* ------------------------------------------------------------------ */

test.describe('Conditional enrolment with an altered skip duration (AM-2827)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'CONDITIONAL',
    enrollForce: true,
    enrollRule: '{{ false }}',
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    enrollSkipTimeSeconds: SHORT_WINDOW_SECONDS,
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('a longer duration still holds when a shorter one has lapsed', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
    matrixDomain,
    adminToken,
    factorId,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2827');

    // A second application in the same domain, differing only in the length of its window.
    const longWindowApp = await quietly(() =>
      createTestApp(uniqueTestName('pw-cond-long-window'), matrixDomain, adminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
        identityProviders: new Set([{ identity: `default-idp-${matrixDomain.id}`, priority: 0 }]),
      }),
    );

    await quietly(() =>
      patchApplication(
        matrixDomain.id,
        adminToken,
        {
          settings: {
            mfa: {
              factor: { defaultFactorId: factorId, applicationFactors: [{ id: factorId, selectionRule: '' }] },
              enroll: {
                active: true,
                type: 'CONDITIONAL',
                forceEnrollment: false,
                enrollmentRule: '{{ false }}',
                enrollmentSkipActive: true,
                enrollmentSkipRule: '{{ true }}',
                skipTimeSeconds: LONG_WINDOW_SECONDS,
              },
              challenge: { active: false },
            },
            advanced: { skipConsent: true },
          },
        },
        longWindowApp.id,
      ),
    );

    const shortClientId = matrixApp.settings.oauth.clientId;
    const longClientId = longWindowApp.settings.oauth.clientId;

    // The skip is stamped on the user; each application measures it against its own window.
    await signIn(page, gatewayUrl, shortClientId, matrixUser.username);
    await skipMfaEnrollment(page);
    const skippedAtMs = Date.now();
    await reachOAuthAuthorizationCallback(page);

    await waitUntilMfaEnrollmentSkipWindowExpired(matrixDomain.id, adminToken, matrixUser.id, SHORT_WINDOW_SECONDS, skippedAtMs);

    await clearSessionOnly(page);
    const onShort = await signIn(page, gatewayUrl, shortClientId, matrixUser.username);
    expect(onShort.askedToEnrol).toBe(true);

    await clearSessionOnly(page);
    const onLong = await signIn(page, gatewayUrl, longClientId, matrixUser.username);
    expect(onLong.askedToEnrol).toBe(false);
  });
});
