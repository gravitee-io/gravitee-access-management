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
  readFirstRecoveryCodeFromPage,
  submitRecoveryCodesContinue,
  openMfaChallengeAlternatives,
  selectRecoveryFactorOnAlternativesPage,
} from '../../../fixtures/login-flows-recovery.fixture';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MOCK_MFA_CODE, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
import { linkJira } from '../../../utils/jira';

test.use({ storageState: { cookies: [], origins: [] } });

test.describe('MFA recovery codes (AM-2216)', () => {
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('AM-2216: enrol mock MFA, capture recovery codes, then sign in with recovery code', async (
    {
      page,
      gatewayUrl,
      recoveryApp,
      recoveryUser,
      recoveryFactorId,
    },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-2216');

    const clientId = recoveryApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, recoveryUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE);

    await page.waitForURL(/.*mfa\/recovery_code.*/i);
    const recoveryCode = await readFirstRecoveryCodeFromPage(page);
    await submitRecoveryCodesContinue(page);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);

    await page.context().clearCookies();

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, recoveryUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await openMfaChallengeAlternatives(page);
    await page.waitForURL(/.*mfa\/challenge\/alternatives.*/i);
    await selectRecoveryFactorOnAlternativesPage(page, recoveryFactorId);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, recoveryCode);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
