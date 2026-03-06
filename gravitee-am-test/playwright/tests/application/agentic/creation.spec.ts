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
import { ApplicationCreationPage } from '../../../pages/application-creation.page';
import { Oauth2SettingsPage } from '../../../pages/oauth2-settings.page';
import { deleteApplication } from '../../../../api/commands/management/application-management-commands';
import { uniqueTestName } from '../../../utils/fixture-helpers';

test.describe('Agentic Application Creation', () => {
  // Jira AM-6561 describes agentic app card visibility in creation wizard.
  // Test verifies card presence; icon assertion deferred (icon type may vary).
  test('AM-6561: wizard shows AGENT card', async ({ page, homePage, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6561');

    await homePage.navigateToDomain(testDomain.name);
    await homePage.clickSidenavItem('Applications');

    const creationPage = new ApplicationCreationPage(page);
    await creationPage.openWizard();

    const agentCard = creationPage.appTypeCard('Agentic Application');
    await expect(agentCard).toBeVisible();
  });

  test('AM-6562: create agentic app with agent card URL', async ({ page, homePage, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-6562', 'AM-6591');
    const appName = uniqueTestName('pw-agent-url');
    let createdAppId: string | undefined;

    try {
      await homePage.navigateToDomain(testDomain.name);
      await homePage.clickSidenavItem('Applications');

      const creationPage = new ApplicationCreationPage(page);
      await creationPage.openWizard();
      await creationPage.selectAppType('Agentic Application');
      await creationPage.clickNext();

      await creationPage.fillAppName(appName);
      await creationPage.fillRedirectUri('https://gravitee.io/callback');
      await creationPage.fillAgentCardUrl('https://example.com/.well-known/agent-card.json');
      await creationPage.clickCreate();

      await creationPage.waitForReady();
      await expect(page).toHaveURL(/.*applications.*/);

      const url = page.url();
      const match = url.match(/applications\/([^/]+)/);
      // eslint-disable-next-line playwright/no-conditional-in-test -- cleanup: extract ID for teardown
      if (match) createdAppId = match[1];
    } finally {
      // eslint-disable-next-line playwright/no-conditional-in-test -- cleanup: delete only if created
      if (createdAppId) {
        try { await deleteApplication(testDomain.id, adminToken, createdAppId); } catch { /* best effort */ }
      }
    }
  });

  test('AM-6563: Create button disabled when required fields missing', async ({ page, homePage, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6563');

    await homePage.navigateToDomain(testDomain.name);
    await homePage.clickSidenavItem('Applications');

    const creationPage = new ApplicationCreationPage(page);
    await creationPage.openWizard();
    await creationPage.selectAppType('Agentic Application');
    await creationPage.clickNext();

    // With no fields filled, Create button should be disabled
    await expect(creationPage.createButton).toBeDisabled();

    // Fill only name — still missing redirect URI
    await creationPage.fillAppName('test-app');
    await expect(creationPage.createButton).toBeDisabled();

    // Fill redirect URI — now Create should be enabled
    await creationPage.fillRedirectUri('https://gravitee.io/callback');
    await expect(creationPage.createButton).toBeEnabled();
  });

  test('AM-6564: OAuth settings show grant_types for AGENT', async ({ page, testDomain, testAgenticApp }, testInfo) => {
    linkJira(testInfo, 'AM-6564');

    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testAgenticApp.id);

    await expect(oauth2Page.grantFlowsSection).toBeVisible();

    // Allowed grants visible
    await expect(oauth2Page.grantTypeCheckbox(/Authorization Code/i)).toBeVisible();
    await expect(oauth2Page.grantTypeCheckbox(/Client Credentials/i)).toBeVisible();

    // Forbidden grants NOT present (filtered out for AGENT)
    await expect(oauth2Page.grantTypeCheckbox(/^Implicit$/i)).toHaveCount(0);
    await expect(oauth2Page.grantTypeCheckbox(/^Password$/i)).toHaveCount(0);
    await expect(oauth2Page.grantTypeCheckbox(/Refresh Token/i)).toHaveCount(0);
  });

  test('response_types not editable in UI for AGENT type', async ({ page, testDomain, testAgenticApp }) => {
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testAgenticApp.id);

    // Positive anchor: wait for grant section to render before negative assertion
    await expect(oauth2Page.grantFlowsSection).toBeVisible();

    await expect(oauth2Page.responseTypeCheckbox(/response.type/i)).toHaveCount(0);
  });

  test('AGENT type restricts implicit and password grant types', async ({ page, testDomain, testAgenticApp }) => {
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testAgenticApp.id);

    await expect(oauth2Page.grantTypeCheckbox(/client.credentials/i)).toBeVisible();

    await expect(oauth2Page.grantTypeCheckbox(/^implicit$/i)).toHaveCount(0);
    await expect(oauth2Page.grantTypeCheckbox(/^password$/i)).toHaveCount(0);
  });

  test('AM-6565: token endpoint auth method defaults and can be selected', async ({ page, testDomain, testAgenticApp }, testInfo) => {
    linkJira(testInfo, 'AM-6565');
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testAgenticApp.id);

    await expect(oauth2Page.tokenAuthMethodSelect).toBeVisible();

    // Verify a default value is displayed (not empty placeholder)
    await expect(oauth2Page.tokenAuthMethodSelect).not.toHaveText('');

    // Open dropdown, verify options present
    await oauth2Page.tokenAuthMethodSelect.click();
    await expect(oauth2Page.tokenAuthMethodOption(/client_secret_basic/)).toBeVisible();
    await expect(oauth2Page.tokenAuthMethodOption(/client_secret_post/)).toBeVisible();

    // 'none' should be disabled for AGENT type
    const noneOption = oauth2Page.tokenAuthMethodOption(/^none$/);
    await expect(noneOption).toBeVisible();
    await expect(noneOption).toHaveAttribute('aria-disabled', 'true');

    // Verify an enabled option can be selected
    await oauth2Page.tokenAuthMethodOption(/client_secret_post/).click();
    await expect(oauth2Page.tokenAuthMethodSelect).toContainText('client_secret_post');
  });

  test('AM-6566: refresh token section hidden for AGENT type', async ({ page, testDomain, testAgenticApp }, testInfo) => {
    linkJira(testInfo, 'AM-6566');
    const oauth2Page = new Oauth2SettingsPage(page);
    await oauth2Page.navigateTo(testDomain.id, testAgenticApp.id);

    await expect(oauth2Page.grantFlowsSection).toBeVisible();
    await expect(oauth2Page.refreshTokenSection).toHaveCount(0);
  });

  test('AM-6590: Agent Card URL field visible in creation wizard', async ({ page, homePage, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6590');
    await homePage.navigateToDomain(testDomain.name);
    await homePage.clickSidenavItem('Applications');

    const creationPage = new ApplicationCreationPage(page);
    await creationPage.openWizard();
    await creationPage.selectAppType('Agentic Application');
    await creationPage.clickNext();

    await expect(creationPage.agentCardUrlInput).toBeVisible();
    await expect(page.getByText('AgentCard URL')).toBeVisible();
  });
});
