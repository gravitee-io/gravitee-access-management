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
  MOCK_MFA_CODE,
} from '../../../fixtures/login-flows-password-mfa-remember.fixture';
import { clearSessionOnly } from '../../../utils/webauthn-helpers';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
import { linkJira } from '../../../utils/jira';

test.use({ storageState: { cookies: [], origins: [] } });

test.describe('Gateway MFA + Remember Device — password login (AM-2218)', () => {
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('AM-2218: after remembering device, second sign-in skips MFA challenge', async (
    { page, gatewayUrl, rememberApp, rememberUser },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-2218');

    const clientId = rememberApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, rememberUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await enrollMockFactor(page);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, MOCK_MFA_CODE, { rememberDevice: true });

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);

    await clearSessionOnly(page);

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, rememberUser.username, API_USER_PASSWORD);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(page.url()).not.toMatch(/mfa\/challenge/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
