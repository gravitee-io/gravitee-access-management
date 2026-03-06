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
import { test, expect } from '../../fixtures/base.fixture';
import { linkJira } from '../../utils/jira';
import { ApplicationCreationPage } from '../../pages/application-creation.page';
import { ApplicationGeneralSettingsPage } from '../../pages/application-general-settings.page';
import { deleteApplication } from '../../../api/commands/management/application-management-commands';
import { uniqueTestName } from '../../utils/fixture-helpers';

/** Application CRUD tests — fixtures handle API setup/teardown. */
test.describe('Application Management', () => {
  test('should display application list in a domain', async ({ homePage, testDomain }) => {
    await homePage.navigateToDomain(testDomain.name);

    await homePage.clickSidenavItem('Applications');
    await homePage.expectUrlMatches(/.*applications.*/);
  });

  test('should show application created via API in the UI list', async ({ page, homePage, testDomain, testApplication }) => {
    await homePage.navigateToDomain(testDomain.name);
    await homePage.clickSidenavItem('Applications');

    const appEntry = page.getByText(testApplication.name, { exact: true });
    await expect(appEntry.first()).toBeVisible();
  });

  test('AM-2223: create application via UI wizard', async ({ page, homePage, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2223');
    const appName = uniqueTestName('pw-web');
    let createdAppId: string | undefined;

    try {
      await homePage.navigateToDomain(testDomain.name);
      await homePage.clickSidenavItem('Applications');

      const creationPage = new ApplicationCreationPage(page);
      await creationPage.openWizard();
      await creationPage.selectAppType('Web');
      await creationPage.clickNext();
      await creationPage.fillAppName(appName);
      await creationPage.fillRedirectUri('https://gravitee.io/callback');
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

  // Jira AM-2222 covers full app update; test validates name change as representative UI field.
  // Other fields (redirect URIs, OAuth settings) tested at API level.
  test('AM-2222: edit application name', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-2222');

    const generalPage = new ApplicationGeneralSettingsPage(page);
    await generalPage.navigateTo(testDomain.id, testApplication.id);

    const updatedName = `${testApplication.name}-edited`;
    await generalPage.fillName(updatedName);
    await generalPage.clickSave();
    await generalPage.expectSnackbar('updated');

    // Verify the name persists on reload
    await page.reload();
    await generalPage.waitForReady();
    await expect(generalPage.nameInput).toHaveValue(updatedName);
  });

  test('AM-2177: delete application', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-2177');

    const generalPage = new ApplicationGeneralSettingsPage(page);
    await generalPage.navigateTo(testDomain.id, testApplication.id);

    await generalPage.clickDelete();
    await generalPage.confirmDialog();

    // Should redirect back to applications list
    await generalPage.waitForReady();
    await expect(page).toHaveURL(/.*applications.*/);

    // Positive anchor: prove the applications list rendered before asserting absence
    await generalPage.waitForPageContent();
    // Verify the application is no longer in the list
    const appEntry = page.getByText(testApplication.name, { exact: true });
    await expect(appEntry).toHaveCount(0);
  });
});
