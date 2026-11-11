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
import { getApplication, listApplications } from '@management-commands/application-management-commands';
import { uniqueTestName } from '../../utils/fixture-helpers';
import { linkJira } from '../../utils/jira';

const applicationsPath = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/applications`;

/** The applications of a domain in the React console. Runs in the console-next project only. */
test.describe('React console: applications', () => {
  test('AM-2223: a web application created through the wizard opens on its overview', async ({
    page,
    context,
    testDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2223');
    const name = uniqueTestName('pw-web');
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);

    await test.step('choose the web type and fill in the settings', async () => {
      await page.goto(`${applicationsPath(testDomain.hrid)}/new`);
      await page.getByRole('radio', { name: 'Traditional web apps' }).click();
      await page.getByRole('button', { name: 'Next' }).click();
      await page.getByRole('textbox', { name: 'Name' }).fill(name);
      await page.getByRole('textbox', { name: 'Redirect URI' }).fill('https://gravitee.io/callback');
      await page.getByRole('button', { name: 'Create application' }).click();
    });

    await test.step('copy the client secret, then open the overview', async () => {
      const dialog = page.getByRole('dialog', { name: 'Copy your client secret' });
      await expect(dialog.getByRole('button', { name: 'I have copied the secret' })).toBeDisabled();
      await dialog.getByRole('button', { name: 'Copy to clipboard' }).click();
      await dialog.getByRole('button', { name: 'I have copied the secret' }).click();
      await expect(page.getByRole('heading', { name: 'Integrate authentication into your application' })).toBeVisible();
    });

    await test.step('the application exists with its redirect URI', async () => {
      const applications = await listApplications(testDomain.id, adminToken);
      const created = applications.data.find((application) => application.name === name);
      expect(created?.type).toBe('web');
      const application = await getApplication(testDomain.id, adminToken, created.id);
      expect(application.settings.oauth.redirectUris).toEqual(['https://gravitee.io/callback']);
    });
  });

  test('AM-2222: an updated name is saved and shown after a reload', async ({
    page,
    testDomain,
    testApplication,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2222');
    const name = `${testApplication.name}-edited`;

    await test.step('change the name and save', async () => {
      await page.goto(`${applicationsPath(testDomain.hrid)}/${testApplication.id}/settings/general`);
      await page.getByRole('textbox', { name: 'Name' }).fill(name);
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText('Application updated')).toBeVisible();
    });

    await test.step('the name persists', async () => {
      expect((await getApplication(testDomain.id, adminToken, testApplication.id)).name).toBe(name);
      await page.reload();
      await expect(page.getByRole('textbox', { name: 'Name' })).toHaveValue(name);
    });
  });

  test('AM-2177: a deleted application leaves the list', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-2177');

    await test.step('delete the application after the confirmation', async () => {
      await page.goto(`${applicationsPath(testDomain.hrid)}/${testApplication.id}/settings/general`);
      await page.getByRole('button', { name: 'Delete', exact: true }).click();
      await page.getByRole('dialog', { name: 'Delete Application' }).getByRole('button', { name: 'Delete' }).click();
      await expect(page.getByText('Application deleted')).toBeVisible();
    });

    await test.step('the list of the domain is empty', async () => {
      await expect(page).toHaveURL(applicationsPath(testDomain.hrid));
      await expect(page.getByText('No applications yet')).toBeVisible();
    });
  });

  test('AM-2175: an added metadata entry is saved and shown after a reload', async ({
    page,
    testDomain,
    testApplication,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-2175');

    await test.step('add a name and value pair, then save', async () => {
      await page.goto(`${applicationsPath(testDomain.hrid)}/${testApplication.id}/settings/metadata`);
      await page.getByRole('textbox', { name: 'Name', exact: true }).fill('test-key');
      await page.getByRole('textbox', { name: 'Value', exact: true }).fill('test-value');
      await page.getByRole('button', { name: 'Add' }).click();
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText('Application updated')).toBeVisible();
    });

    await test.step('the entry persists', async () => {
      expect((await getApplication(testDomain.id, adminToken, testApplication.id)).metadata).toEqual({ 'test-key': 'test-value' });
      await page.reload();
      await expect(page.getByRole('textbox', { name: 'Value of test-key' })).toHaveValue('test-value');
    });
  });
});
