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
  reachOAuthAuthorizationCallback,
  skipMfaEnrollment,
  submitLogin,
  waitUntilMfaEnrollmentSkipWindowExpired,
} from '../../utils/mfa-helpers';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

/**
 * AM-2821 / UC-AM-MFA3 — the skip duration on optional enrolment.
 *
 * The window is judged as `skippedAt + skipTimeSeconds > now`, where the timestamp belongs to the
 * user and the duration to the application. One skip can therefore be measured against two
 * different windows, which is what the second test relies on.
 */
const SHORT_WINDOW_SECONDS = 3;

/** Long enough that it cannot lapse during the run, so a difference can only come from the setting. */
const LONG_WINDOW_SECONDS = 3600;

test.describe('Optional enrolment skip duration (AM-2821)', () => {
  test.use({
    enrollActive: true,
    enrollType: 'OPTIONAL',
    enrollForce: false,
    enrollSkipActive: true,
    enrollSkipRule: '{{ true }}',
    enrollSkipTimeSeconds: SHORT_WINDOW_SECONDS,
    challengeActive: false,
  });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  /** Signs in and reports whether enrolment was asked for. */
  const signInAndSeeWhetherAsked = async (page, gatewayUrl: string, clientId: string, username: string) => {
    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, username, API_USER_PASSWORD);
    await page.waitForURL((url) => /\/mfa\/enroll/i.test(url.href) || url.searchParams.has('code'));
    return { askedToEnrol: /\/mfa\/enroll/i.test(page.url()) };
  };

  test('the user is left alone inside the skip window and asked again once it passes', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
    matrixDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2821');

    const clientId = matrixApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await skipMfaEnrollment(page);
    const skippedAtMs = Date.now();

    await reachOAuthAuthorizationCallback(page);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);

    // Straight back in, well inside the window: the skip is still standing.
    await clearSessionOnly(page);
    const inside = await signInAndSeeWhetherAsked(page, gatewayUrl, clientId, matrixUser.username);
    expect(inside.askedToEnrol).toBe(false);

    // The Management API is polled rather than the clock slept on, so the wait is exactly as long
    // as the window actually needs.
    await waitUntilMfaEnrollmentSkipWindowExpired(matrixDomain.id, adminToken, matrixUser.id, SHORT_WINDOW_SECONDS, skippedAtMs);

    await clearSessionOnly(page);
    const after = await signInAndSeeWhetherAsked(page, gatewayUrl, clientId, matrixUser.username);
    expect(after.askedToEnrol).toBe(true);

    // Asking again offers the skip a second time rather than forcing enrolment.
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();
    await expect(page.locator('button[name="user_mfa_enrollment"][value="false"]')).toBeVisible();
  });

  test('a longer window still holds when a shorter one has already lapsed', async ({
    page,
    gatewayUrl,
    matrixApp,
    matrixUser,
    matrixDomain,
    adminToken,
    factorId,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2821');

    // A second application in the same domain, differing only in the length of its window.
    const longWindowApp = await quietly(() =>
      createTestApp(uniqueTestName('pw-long-window-app'), matrixDomain, adminToken, 'WEB', {
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
                type: 'OPTIONAL',
                forceEnrollment: false,
                enrollmentSkipActive: true,
                enrollmentSkipRule: '{{ true }}',
                skipTimeSeconds: LONG_WINDOW_SECONDS,
              },
              challenge: { active: false },
            },
            // The consent page is not part of this behaviour; skipping it lets the sign-in run
            // through to the callback the same way the fixture's own application does.
            advanced: { skipConsent: true },
          },
        },
        longWindowApp.id,
      ),
    );

    const shortClientId = matrixApp.settings.oauth.clientId;
    const longClientId = longWindowApp.settings.oauth.clientId;

    // Skipping once stamps the user; both applications read that same stamp.
    await page.goto(buildAuthorizeUrl(gatewayUrl, shortClientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, matrixUser.username, API_USER_PASSWORD);
    await page.waitForURL(/.*mfa\/enroll.*/i);
    await skipMfaEnrollment(page);
    const skippedAtMs = Date.now();
    await reachOAuthAuthorizationCallback(page);

    await waitUntilMfaEnrollmentSkipWindowExpired(matrixDomain.id, adminToken, matrixUser.id, SHORT_WINDOW_SECONDS, skippedAtMs);

    // The short window has lapsed, so this application asks again.
    await clearSessionOnly(page);
    const onShort = await signInAndSeeWhetherAsked(page, gatewayUrl, shortClientId, matrixUser.username);
    expect(onShort.askedToEnrol).toBe(true);

    // Same user, same skip, same moment — the longer window has not lapsed, so this one does not.
    await clearSessionOnly(page);
    const onLong = await signInAndSeeWhetherAsked(page, gatewayUrl, longClientId, matrixUser.username);
    expect(onLong.askedToEnrol).toBe(false);
  });
});
