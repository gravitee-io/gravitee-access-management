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
import { getDomain } from '@management-commands/domain-management-commands';
import { uniqueTestName } from '../../utils/fixture-helpers';
import { linkJira } from '../../utils/jira';

const domainsPath = () => `/environments/${process.env.AM_DEF_ENV_HRID}/domains`;

test.describe('React console: domain settings, general', () => {
  test('AM-2215: an updated name and description are saved and shown after a reload', async ({
    page,
    testDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2215');
    const name = uniqueTestName('pw-renamed');
    const description = 'Updated by the console-next spec';

    await test.step('change the name and the description, then save', async () => {
      await page.goto(`${domainsPath()}/${testDomain.hrid}/settings/general`);
      await page.getByRole('textbox', { name: 'Name' }).fill(name);
      await page.getByRole('textbox', { name: 'Description' }).fill(description);
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText(`Domain ${name} updated`)).toBeVisible();
    });

    await test.step('the page follows the new hrid and keeps the values after a reload', async () => {
      const updated = await getDomain(testDomain.id, adminToken);
      expect(updated.name).toBe(name);
      expect(updated.description).toBe(description);
      await expect(page).toHaveURL(`${domainsPath()}/${updated.hrid}/settings/general`);

      await page.reload();
      await expect(page.getByRole('textbox', { name: 'Name' })).toHaveValue(name);
      await expect(page.getByRole('textbox', { name: 'Description' })).toHaveValue(description);
    });
  });

  test('AM-2212: a disabled domain is deleted and leaves the domains list', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2212');

    await test.step('disable the domain and save', async () => {
      await page.goto(`${domainsPath()}/${testDomain.hrid}/settings/general`);
      await expect(page.getByRole('switch', { name: 'Enable domain' })).toBeChecked();
      await page.getByRole('switch', { name: 'Enable domain' }).click();
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText(`Domain ${testDomain.name} updated`)).toBeVisible();
      expect((await getDomain(testDomain.id, adminToken)).enabled).toBe(false);
    });

    await test.step('delete the domain after the confirmation', async () => {
      await page.getByRole('button', { name: 'Delete', exact: true }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Delete domain' }).click();
      await expect(page.getByText(`Domain ${testDomain.name} deleted`)).toBeVisible();
      await expect(page).toHaveURL(domainsPath());
    });

    await test.step('the domains list no longer finds the domain', async () => {
      await expect(page.getByRole('table')).toBeVisible();
      await page.getByRole('searchbox', { name: 'Search domains' }).fill(testDomain.name);
      await expect(page.getByRole('table').getByRole('link', { name: testDomain.name })).toHaveCount(0);
    });
  });
});
