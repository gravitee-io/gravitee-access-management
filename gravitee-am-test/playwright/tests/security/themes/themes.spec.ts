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
import { expect, test as themeTest } from '../../../fixtures/theme-gateway.fixture';
import { test as loginFormTest } from '../../../fixtures/login-form-gateway.fixture';
import { linkJira } from '../../../utils/jira';
import { buildAuthorizeUrl } from '../../../utils/mfa-helpers';
import { AM2193_LOGIN_FORM_MARKER_TEXT, GATEWAY_THEME_LOGO_URL, GATEWAY_THEME_PRIMARY_BUTTON_RGB } from '../../../utils/test-constants';

themeTest.describe('Gateway domain theme (AM-2172)', () => {
  themeTest.use({ storageState: { cookies: [], origins: [] } });

  themeTest('AM-2172: login page shows themed logo and primary button colour', async ({ page, themeGatewayBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2172');

    const clientId = themeGatewayBundle.app.settings.oauth.clientId;
    await page.goto(buildAuthorizeUrl(themeGatewayBundle.gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);

    await expect(page.locator('img.logo')).toHaveAttribute('src', GATEWAY_THEME_LOGO_URL);

    const submitBtn = page.locator('#submitBtn');
    await expect(submitBtn).toBeVisible();
    await expect(submitBtn).toHaveCSS('background-color', GATEWAY_THEME_PRIMARY_BUTTON_RGB);
  });
});

loginFormTest.describe('Gateway domain login form override (AM-2193)', () => {
  loginFormTest.use({ storageState: { cookies: [], origins: [] } });

  loginFormTest('AM-2193: overridden LOGIN form shows custom marker on gateway', async ({ page, loginFormGatewayBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2193');

    const clientId = loginFormGatewayBundle.app.settings.oauth.clientId;
    await page.goto(buildAuthorizeUrl(loginFormGatewayBundle.gatewayUrl, clientId));
    await page.waitForURL(/.*login.*/i);

    await expect(page.locator('#pw-am-2193-marker')).toHaveText(AM2193_LOGIN_FORM_MARKER_TEXT);
  });
});
