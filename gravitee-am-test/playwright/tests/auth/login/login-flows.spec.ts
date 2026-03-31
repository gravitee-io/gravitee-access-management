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
import { test as consoleTest } from '../../../fixtures/base.fixture';
import { test as gatewayTest } from '../../../fixtures/login-flows-gateway.fixture';
import { linkJira } from '../../../utils/jira';
import { buildAuthorizeUrl, submitLogin, handleConsentIfPresent } from '../../../utils/mfa-helpers';
import { ADMIN_PASSWORD, AUTH_CODE_FORMAT } from '../../../utils/test-constants';

consoleTest.describe('Console admin login (AM-2230)', () => {
  consoleTest.use({ storageState: { cookies: [], origins: [] } });

  consoleTest('AM-2230: admin signs in and sees the console shell', async ({ loginPage, page }, testInfo) => {
    linkJira(testInfo, 'AM-2230');

    const username = process.env.AM_ADMIN_USERNAME || 'admin';
    const password = process.env.AM_ADMIN_PASSWORD || ADMIN_PASSWORD;

    await loginPage.goto();
    await loginPage.login(username, password);

    await expect(page.locator('.gio-side-nav').first()).toBeVisible();
    await expect(page).toHaveURL(/(?:environments|dashboard|domains)/i);
  });
});

consoleTest.describe('Console alternate admin (AM-2192)', () => {
  consoleTest.use({ storageState: { cookies: [], origins: [] } });

  consoleTest('AM-2192: login with non-default admin credentials when configured', async ({ loginPage, page }, testInfo) => {
    linkJira(testInfo, 'AM-2192');

    // eslint-disable-next-line playwright/no-skipped-test -- optional stack configuration
    consoleTest.skip(
      !process.env.AM_ALT_ADMIN_USERNAME?.trim() || !process.env.AM_ALT_ADMIN_PASSWORD?.trim(),
      'Set AM_ALT_ADMIN_USERNAME and AM_ALT_ADMIN_PASSWORD (user must exist for the Management API realm)',
    );

    const username = process.env.AM_ALT_ADMIN_USERNAME!.trim();
    const password = process.env.AM_ALT_ADMIN_PASSWORD!.trim();

    await loginPage.goto();
    await loginPage.login(username, password);

    await expect(page.locator('.gio-side-nav').first()).toBeVisible();
  });
});

gatewayTest.describe('Gateway identifier-first login (AM-2170)', () => {
  gatewayTest.use({ storageState: { cookies: [], origins: [] } });

  gatewayTest('AM-2170: OAuth login uses identifier step then password', async ({ page, loginFlowsBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2170');

    const clientId = loginFlowsBundle.appIdentifierFirst.settings.oauth.clientId;
    const { username, password } = loginFlowsBundle.userIdentifierFirst;

    await page.goto(buildAuthorizeUrl(loginFlowsBundle.gatewayUrl, clientId));
    await page.waitForURL(/login\/identifier/i);

    await page.locator('#username').fill(username);
    await page.locator('#submitBtn').click();

    await expect(page.locator('#password')).toBeVisible();
    await page.locator('#password').fill(password);
    await page.locator('#submitBtn').click();

    await handleConsentIfPresent(page);
    await page.waitForURL(/callback\?code=/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

gatewayTest.describe('Gateway IdP selection rules (AM-2819)', () => {
  gatewayTest.use({ storageState: { cookies: [], origins: [] } });

  gatewayTest('AM-2819: first username is routed to the matching inline IdP', async ({ page, loginFlowsBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2819');

    const clientId = loginFlowsBundle.appIdpDual.settings.oauth.clientId;
    const { username, password } = loginFlowsBundle.userIdpSel1;

    await page.goto(buildAuthorizeUrl(loginFlowsBundle.gatewayUrl, clientId));
    await page.waitForURL(/login/i);
    await submitLogin(page, username, password);
    await handleConsentIfPresent(page);
    await page.waitForURL(/callback\?code=/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });

  gatewayTest('AM-2819: second username is routed to the other inline IdP', async ({ page, loginFlowsBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2819');

    const clientId = loginFlowsBundle.appIdpDual.settings.oauth.clientId;
    const { username, password } = loginFlowsBundle.userIdpSel2;

    await page.goto(buildAuthorizeUrl(loginFlowsBundle.gatewayUrl, clientId));
    await page.waitForURL(/login/i);
    await submitLogin(page, username, password);
    await handleConsentIfPresent(page);
    await page.waitForURL(/callback\?code=/i);
    expect(new URL(page.url()).searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

