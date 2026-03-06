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
import { ApplicationGeneralSettingsPage } from '../../../pages/application-general-settings.page';
import { AgentMetadataPage } from '../../../pages/agent-metadata.page';
import { submenuItem } from '../../../utils/selectors';
import { patchApplication } from '../../../../api/commands/management/application-management-commands';

test.describe('Agentic Application — Agent Metadata', () => {
  test('Agent Card URL editable in General settings', async ({ page, testDomain, testAgenticApp }) => {
    const generalPage = new ApplicationGeneralSettingsPage(page);
    await generalPage.navigateTo(testDomain.id, testAgenticApp.id);

    await expect(generalPage.agentCardUrlInput).toBeVisible();
    await expect(generalPage.agentCardUrlInput).toBeEnabled();
  });

  test('AM-6595: Agent Card URL update persists on reload', async ({ page, testDomain, testAgenticApp }, testInfo) => {
    linkJira(testInfo, 'AM-6595');
    const generalPage = new ApplicationGeneralSettingsPage(page);
    await generalPage.navigateTo(testDomain.id, testAgenticApp.id);

    const newUrl = 'https://updated-example.com/.well-known/agent-card.json';
    await generalPage.fillAgentCardUrl(newUrl);
    await generalPage.clickSave();
    await generalPage.expectSnackbar('updated');

    // Reload and verify persistence
    await page.reload();
    await generalPage.waitForReady();
    await expect(generalPage.agentCardUrlInput).toHaveValue(newUrl);
  });

  test('AM-6592: Agent Metadata tab visible for AGENT type only', async ({ page, testDomain, testAgenticApp, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-6592');

    const envHrid = process.env.AM_DEF_ENV_HRID || 'default';
    const generalPage = new ApplicationGeneralSettingsPage(page);

    // Positive: Agent Metadata tab IS visible for AGENT type
    await page.goto(`/environments/${envHrid}/domains/${testDomain.id}/applications/${testAgenticApp.id}`);
    await generalPage.waitForReady();

    await expect(submenuItem(page, 'Agent Metadata')).toBeVisible();

    // Negative: Agent Metadata tab is NOT visible for non-AGENT type
    await page.goto(`/environments/${envHrid}/domains/${testDomain.id}/applications/${testApplication.id}`);
    await generalPage.waitForReady();

    // Positive anchor: wait for page content before negative assertion
    await generalPage.waitForPageContent();

    await expect(submenuItem(page, 'Agent Metadata')).toHaveCount(0);
  });

  test('AM-6593: no-URL state when agent card URL is empty', async ({ page, testDomain, testAgenticApp }, testInfo) => {
    linkJira(testInfo, 'AM-6593');

    const metadataPage = new AgentMetadataPage(page);
    await metadataPage.navigateTo(testDomain.id, testAgenticApp.id);

    await metadataPage.expectNoUrlState();
  });

  // Jira AM-6594 describes agent card info display. External URL may be unreachable;
  // test accepts content OR error to avoid false negatives. Core check: page transitions
  // beyond no-URL state.
  test('AM-6594: loading then content state with valid URL', async ({ page, adminToken, testDomain, testAgenticApp }, testInfo) => {
    linkJira(testInfo, 'AM-6594');

    // Set a URL via API first — agentCardUrl is under settings.advanced
    await patchApplication(testDomain.id, adminToken, {
      settings: {
        advanced: {
          agentCardUrl: 'https://hello-world-gxfr.onrender.com/.well-known/agent.json',
        },
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any -- TODO: remove cast when agentCardUrl is added to PatchApplication SDK type (regenerate SDK after OpenAPI spec update)
    } as any, testAgenticApp.id);

    const metadataPage = new AgentMetadataPage(page);
    await metadataPage.navigateTo(testDomain.id, testAgenticApp.id);

    // The page should show loading then either content or error (not the no-URL state).
    // Either outcome is valid — the URL may or may not be reachable. We verify
    // the page transitions beyond the no-URL state by checking for the source URL element.
    await expect(page.locator('.agent-card-url')).toBeVisible();

    await metadataPage.expectContentOrError();
  });
});
