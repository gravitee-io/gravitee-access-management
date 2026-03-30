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
  DEFAULT_SELECTION_MOCK_CODE,
} from '../../fixtures/mfa-default-factor-selection.fixture';
import { linkJira } from '../../utils/jira';
import { API_USER_PASSWORD, AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

test.use({ storageState: { cookies: [], origins: [] } });

test.describe('MFA default factor when selection rules miss (AM-2820)', () => {
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

  test('AM-2820: enrollment lists only the default factor; challenge uses default mock code', async (
    { page, gatewayUrl, defApp, defUser },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-2820');

    const clientId = defApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, defUser.username, API_USER_PASSWORD);

    await page.waitForURL(/.*mfa\/enroll.*/i);
    await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

    const radios = page.locator('input[type="radio"][name="factorId"]');
    await expect(radios).toHaveCount(1);

    await enrollMockFactor(page, 0);

    await page.waitForURL(/.*mfa\/challenge.*/i);
    await completeMfaChallenge(page, DEFAULT_SELECTION_MOCK_CODE);

    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
