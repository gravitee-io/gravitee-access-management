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
import { test, expect, buildAuthorizeUrl, submitLogin, handleConsentIfPresent } from '../../../fixtures/login-flows-role-mapper.fixture';
import { listUsers } from '@management-commands/user-management-commands';
import { reachOAuthAuthorizationCallback } from '../../../utils/mfa-helpers';
import { AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
import { linkJira } from '../../../utils/jira';

test.use({ storageState: { cookies: [], origins: [] } });

test.describe('IdP role mapper (AM-2219)', () => {
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT * 2);

  test('AM-2219: user matching mapper rule receives mapped domain role', async (
    { page, gatewayUrl, mapperApp, mapperDomain, adminToken, gatewayUser, mappedRoleName },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-2219');

    const clientId = mapperApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);
    await submitLogin(page, gatewayUser.username, gatewayUser.password);

    await handleConsentIfPresent(page);
    await reachOAuthAuthorizationCallback(page, { iterations: 32, consentTimeoutMs: 10_000 });
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);

    const userPage = await listUsers(mapperDomain.id, adminToken, gatewayUser.username);
    expect(userPage.totalCount).toBeGreaterThan(0);
    const user = userPage.data[0];
    expect(user.dynamicRoles ?? []).toContain(mappedRoleName);
  });
});
