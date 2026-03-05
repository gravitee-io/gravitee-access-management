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
import { test, expect } from '../../../fixtures/base.fixture';
import { linkJira } from '../../../utils/jira';
import { DomainTokenExchangePage } from '../../../pages/domain-token-exchange.page';
import { Oauth2SettingsPage } from '../../../pages/oauth2-settings.page';
import { performPost } from '../../../../api/commands/gateway/oauth-oidc-commands';
import { waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { waitForNextSync } from '../../../../api/commands/gateway/monitoring-commands';
import { getAllIdps } from '../../../../api/commands/management/idp-management-commands';
import { buildCreateAndTestUser } from '../../../../api/commands/management/user-management-commands';
import { createTestApp } from '../../../../api/commands/utils/application-commands';
import { applicationBase64Token } from '../../../../api/commands/gateway/utils';
import { fetchOidcConfig, obtainSubjectToken } from '../../../utils/token-exchange-helpers';
import { quietly, uniqueTestName } from '../../../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../../../utils/test-constants';

// Jira AM-6641 has 5 steps; tests cover steps 1+3 (enable+verify scope handling).
// Missing: default value check (step 1 precondition), inherited state (step 2),
// toggle back to inherited (step 4), MCP Server (step 5).
test.describe('Token Exchange Configuration', () => {
  test('AM-6641: domain scope handling — set permissive, save, verify persistence', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6641');

    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);

    // Enable token exchange first (fields are behind *ngIf)
    await tePage.toggleEnable();
    await expect(tePage.scopeHandlingSelect).toBeVisible();

    // Select "Permissive" scope handling
    await tePage.selectScopeHandling(/permissive/i);
    await expect(tePage.saveButton).toBeEnabled();
    await tePage.saveButton.click();
    await tePage.expectSnackbar(/saved|updated/i);

    // Reload page and verify persistence
    await tePage.navigateTo(testDomain.id);
    await expect(tePage.scopeHandlingSelect).toContainText(/permissive/i);
  });

  test('AM-6641: domain scope handling — set downscoping, save, verify persistence', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6641');

    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);

    await tePage.toggleEnable();
    await expect(tePage.scopeHandlingSelect).toBeVisible();

    await tePage.selectScopeHandling(/downscoping/i);
    await expect(tePage.saveButton).toBeEnabled();
    await tePage.saveButton.click();
    await tePage.expectSnackbar(/saved|updated/i);

    // Reload and verify
    await tePage.navigateTo(testDomain.id);
    await expect(tePage.scopeHandlingSelect).toContainText(/downscoping/i);
  });

  test('AM-6641: app-level scope handling override', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-6641');

    // First enable token exchange on the domain
    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);
    await tePage.toggleEnable();
    await expect(tePage.saveButton).toBeEnabled();
    await tePage.saveButton.click();
    await tePage.expectSnackbar(/saved|updated/i);

    // Navigate to app OAuth2 settings
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testApplication.id);

    // Positive anchor: wait for grant flows section
    await expect(oauth2Page.grantFlowsSection).toBeVisible();

    // Enable Token Exchange grant type on the app first
    const teCheckbox = oauth2Page.grantTypeCheckbox(/token exchange/i);
    await expect(teCheckbox).toBeVisible();
    await teCheckbox.click();
    await oauth2Page.waitForReady();

    // Now the inherit toggle becomes enabled
    await expect(oauth2Page.inheritanceToggle).toBeEnabled();

    // Uncheck inherit to reveal app-level scope handling
    await oauth2Page.inheritanceToggle.click();
    await oauth2Page.waitForReady();

    // After unchecking inherit, the scope handling select must appear
    await expect(oauth2Page.tokenExchangeScopeSelect).toBeVisible();

    // Select an option
    await oauth2Page.tokenExchangeScopeSelect.click();
    await page.locator('mat-option').filter({ hasText: /permissive/i }).click();
  });

  test('AM-6641: default scope handling value before any change', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6641');

    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);

    // Enable token exchange to reveal scope handling select
    await tePage.toggleEnable();
    await expect(tePage.scopeHandlingSelect).toBeVisible();

    // Default value should be "Downscoping"
    await expect(tePage.scopeHandlingSelect).toContainText(/downscoping/i);
  });

  test('AM-6641: toggle back to inherited state after override', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-6641');

    // First enable token exchange on the domain
    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);
    await tePage.toggleEnable();
    await expect(tePage.saveButton).toBeEnabled();
    await tePage.saveButton.click();
    await tePage.expectSnackbar(/saved|updated/i);

    // Navigate to app OAuth2 settings
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testApplication.id);

    await expect(oauth2Page.grantFlowsSection).toBeVisible();

    // Enable Token Exchange grant type
    const teCheckbox = oauth2Page.grantTypeCheckbox(/token exchange/i);
    await expect(teCheckbox).toBeVisible();
    await teCheckbox.click();
    await oauth2Page.waitForReady();

    await expect(oauth2Page.inheritanceToggle).toBeEnabled();

    // Uncheck inherit
    await oauth2Page.inheritanceToggle.click();
    await oauth2Page.waitForReady();

    await expect(oauth2Page.tokenExchangeScopeSelect).toBeVisible();

    // Toggle back to inherited — scope select should disappear
    await oauth2Page.inheritanceToggle.click();
    await oauth2Page.waitForReady();

    await expect(oauth2Page.tokenExchangeScopeSelect).toBeHidden();
  });

  test('AM-6475: token exchange grant disabled on app when domain disables it', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-6475');

    // Domain token exchange is NOT enabled (default state)
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testApplication.id);

    await expect(oauth2Page.grantFlowsSection).toBeVisible();

    // Token Exchange checkbox is visible but disabled when domain TE is off
    const teCheckbox = oauth2Page.grantTypeCheckbox(/token exchange/i);
    await expect(teCheckbox).toBeVisible();
    await expect(teCheckbox.locator('input[type="checkbox"]')).toBeDisabled();
  });

  test('AM-6475: token exchange grant enabled on app when domain enables it', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-6475');

    // Enable token exchange on the domain first
    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);
    await tePage.toggleEnable();
    await expect(tePage.saveButton).toBeEnabled();
    await tePage.saveButton.click();
    await tePage.expectSnackbar(/saved|updated/i);

    // Now navigate to app OAuth2 settings
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testApplication.id);

    await expect(oauth2Page.grantFlowsSection).toBeVisible();

    // Token Exchange grant type should now be available and enabled
    const teCheckbox = oauth2Page.grantTypeCheckbox(/token exchange/i);
    await expect(teCheckbox).toBeVisible();
    await expect(teCheckbox.locator('input[type="checkbox"]')).toBeEnabled();
  });

  test('AM-6475: API returns 400 when token exchange is disabled on domain', async ({ adminToken, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6475');

    // Create a WEB app with password grant + token exchange grant type
    const idps = await quietly(() => getAllIdps(testDomain.id, adminToken));
    const defaultIdp = idps.find((idp) => idp.external === false);
    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-te-disabled'), testDomain, adminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: ['https://gravitee.io/callback'],
            grantTypes: ['password', 'urn:ietf:params:oauth:grant-type:token-exchange'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
        identityProviders: defaultIdp ? new Set([{ identity: defaultIdp.id, priority: 0 }]) : new Set(),
      }),
    );
    const user = await quietly(() => buildCreateAndTestUser(testDomain.id, adminToken, 0));

    await quietly(() => waitForNextSync(testDomain.id));
    await quietly(() => waitForOidcReady(testDomain.hrid));
    const oidc = await quietly(() => fetchOidcConfig(testDomain.hrid));
    const basicAuth = applicationBase64Token(app);

    // Obtain a subject token via password grant
    const tokens = await obtainSubjectToken(oidc.token_endpoint, basicAuth, user.username, API_USER_PASSWORD, 'openid');

    // Token exchange is NOT enabled on domain — API call should return 400
    await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:token-exchange')}` +
      `&subject_token=${encodeURIComponent(tokens.accessToken)}` +
      `&subject_token_type=${encodeURIComponent('urn:ietf:params:oauth:token-type:access_token')}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);
  });
});
