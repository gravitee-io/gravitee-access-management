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
import { DomainGeneralPage } from '../../pages/domain-general.page';

/** Domain CRUD tests — testDomain fixture handles API setup/teardown. */
test.describe('Domain Management', () => {
  test('should display the domain list', async ({ homePage }) => {
    await homePage.gotoDomainsList();

    await expect(homePage.domainsContent).toBeVisible();
  });

  test('should navigate into a domain created via API', async ({ homePage, testDomain }) => {
    await homePage.navigateToDomain(testDomain.name);

    await homePage.expectUrlMatches(/.*domains.*/);
  });

  test('should show domain detail page with sidenav', async ({ homePage, testDomain }) => {
    await homePage.navigateToDomain(testDomain.name);

    await expect(homePage.sidenav.locator('a').first()).toBeVisible();
  });

  test('AM-2215: edit domain name and description', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-2215');

    const generalPage = new DomainGeneralPage(page);
    await generalPage.navigateTo(testDomain.id);

    const updatedName = `${testDomain.name}-edited`;
    const updatedDesc = 'Updated description for Playwright test';
    await generalPage.fillName(updatedName);
    await generalPage.fillDescription(updatedDesc);
    await generalPage.clickSave();
    await generalPage.expectSnackbar('updated');

    // Verify persistence on reload
    await page.reload();
    await generalPage.waitForReady();
    await expect(generalPage.nameInput).toHaveValue(updatedName);
    await expect(generalPage.descriptionInput).toHaveValue(updatedDesc);
  });

  test('AM-2212: disable and delete domain', async ({ page, homePage, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-2212');

    const generalPage = new DomainGeneralPage(page);
    await generalPage.navigateTo(testDomain.id);

    // Disable the domain first (required before deletion)
    await generalPage.toggleEnabled();
    await generalPage.waitForReady();

    // Click delete in the danger zone
    await generalPage.clickDelete();
    await generalPage.confirmDialog();

    // Should redirect to domains list
    await generalPage.waitForReady();
    await expect(page).toHaveURL(/.*domains.*/);

    // Verify domain no longer in list
    await expect(homePage.domainLink(testDomain.name)).toHaveCount(0);
  });
});
