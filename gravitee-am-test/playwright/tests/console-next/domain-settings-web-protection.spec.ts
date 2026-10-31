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
import type { Page } from '@playwright/test';
import { test, expect } from '../../fixtures/base.fixture';
import { getDomain } from '@management-commands/domain-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { createCustomOrganizationRole, deleteOrganizationRole } from '@management-commands/role-management-commands';
import { addDomainMembership, userMembership } from '@management-commands/membership-management-commands';
import { NewRoleAssignableTypeEnum } from '@management-models/NewRole';
import { createConsolePersona, signInToConsole, workerScope } from '../../utils/permissions-helpers';
import { quietly, uniqueTestName } from '../../utils/fixture-helpers';
import { linkJira } from '../../utils/jira';

const section = (page: Page, title: string) => page.locator('[data-slot="card"]').filter({ has: page.getByText(title, { exact: true }) });

const webProtectionPath = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/web-protection`;

test.describe('React console: domain settings, web protection', () => {
  test('AM-2502: a negative CORS max age marks the field invalid and blocks the save', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-2502');
    const cors = section(page, 'Cross-origin resource sharing (CORS)');

    await page.goto(webProtectionPath(testDomain.hrid));
    await cors.getByRole('switch', { name: 'Inherit platform settings' }).click();
    await cors.getByRole('switch', { name: 'Enable CORS' }).click();
    await cors.getByRole('spinbutton', { name: 'Max age (seconds)' }).fill('-55');

    await expect(cors.getByRole('spinbutton', { name: 'Max age (seconds)' })).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  test('AM-7371: the owner saves a web protection setting and sees it after a reload', async ({
    page,
    testDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7371');
    const xframe = section(page, 'X-Frame-Options');

    await test.step('override X-Frame-Options with SAMEORIGIN, then save', async () => {
      await page.goto(webProtectionPath(testDomain.hrid));
      await xframe.getByRole('switch', { name: 'Inherit platform settings' }).click();
      await xframe.getByRole('switch', { name: 'Enable X-Frame-Options' }).click();
      await xframe.getByRole('combobox', { name: 'Action' }).click();
      await page.getByRole('option', { name: 'SAMEORIGIN' }).click();
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText(`Web protection settings for ${testDomain.name} updated`)).toBeVisible();
    });

    await test.step('the domain keeps the override after a reload', async () => {
      const { webProtectionSettings } = await getDomain(testDomain.id, adminToken);
      expect(webProtectionSettings.xframe).toEqual({ inherited: false, enabled: true, action: 'SAMEORIGIN' });

      await page.reload();
      await expect(xframe.getByRole('combobox', { name: 'Action' })).toHaveText('SAMEORIGIN');
    });
  });

  test('AM-7371: a user who can only read the settings gets disabled fields and no save', async ({
    page,
    testDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7371');
    const role = await createCustomOrganizationRole(
      adminToken,
      uniqueTestName(workerScope('pw-settings-reader')),
      NewRoleAssignableTypeEnum.Domain,
      ['domain_read', 'domain_settings_read'],
    );
    const reader = await createConsolePersona(adminToken, 'settingsreader');

    try {
      await quietly(() => addDomainMembership(testDomain.id, adminToken, userMembership(reader.userId, role.id)));
      await page.context().clearCookies();
      await signInToConsole(page, reader);

      await page.goto(webProtectionPath(testDomain.hrid));
      await expect(page.getByRole('heading', { level: 1, name: 'Web Protection' })).toBeVisible();
      // A new domain inherits every section, so only the four inherit switches show.
      const switches = page.getByRole('main').getByRole('switch', { name: 'Inherit platform settings' });
      await expect(switches).toHaveCount(4);
      for (const control of await switches.all()) {
        await expect(control).toBeDisabled();
      }
      await expect(page.getByRole('button', { name: 'Save' })).toHaveCount(0);
    } finally {
      await quietly(() => deleteOrganisationUser(adminToken, reader.userId)).catch(() => undefined);
      await quietly(() => deleteOrganizationRole(adminToken, role.id)).catch(() => undefined);
    }
  });
});
