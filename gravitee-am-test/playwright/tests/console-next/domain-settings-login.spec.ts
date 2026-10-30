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
import { linkJira } from '../../utils/jira';

const CBA_SWITCH = 'Passwordless certificate-based authentication (CBA)';
const CBA_URL = 'Certificate-based authentication base URL';

test.describe('React console: domain settings, login', () => {
  test('AM-6055: the CBA toggle is in the passwordless section of the domain login settings', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-6055');

    await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains/${testDomain.hrid}/settings/login`);
    const passwordless = page.locator('[data-slot="card"]').filter({ has: page.getByText('Passwordless', { exact: true }) });
    await expect(passwordless.getByRole('switch', { name: CBA_SWITCH })).toBeVisible();
  });

  test('certificate-based authentication is saved with its base URL and shown after a reload', async ({ page, testDomain, adminToken }) => {
    const baseUrl = 'https://cba.example.com';

    await test.step('turn on CBA with a base URL, then save', async () => {
      await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains/${testDomain.hrid}/settings/login`);
      await page.getByRole('switch', { name: CBA_SWITCH }).click();
      await page.getByRole('textbox', { name: CBA_URL }).fill(baseUrl);
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText('Login configuration updated')).toBeVisible();
    });

    await test.step('the domain keeps the CBA settings after a reload', async () => {
      const { loginSettings } = await getDomain(testDomain.id, adminToken);
      expect(loginSettings).toMatchObject({ inherited: false, certificateBasedAuthEnabled: true, certificateBasedAuthUrl: baseUrl });

      await page.reload();
      await expect(page.getByRole('switch', { name: CBA_SWITCH })).toBeChecked();
      await expect(page.getByRole('textbox', { name: CBA_URL })).toHaveValue(baseUrl);
    });
  });
});
