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

test.describe('React console: domain settings, user accounts', () => {
  test('brute force detection is saved with the Angular defaults and shown after a reload', async ({ page, testDomain, adminToken }) => {
    await test.step('turn on brute force detection, then save', async () => {
      await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains/${testDomain.hrid}/settings/account`);
      await page.getByRole('switch', { name: 'Brute force detection', exact: true }).click();
      await expect(page.getByRole('spinbutton', { name: 'Max login attempts' })).toHaveValue('10');
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText('User Accounts Settings updated')).toBeVisible();
    });

    await test.step('the domain keeps the settings in seconds, shown in their largest unit after a reload', async () => {
      const { accountSettings } = await getDomain(testDomain.id, adminToken);
      expect(accountSettings).toMatchObject({
        inherited: false,
        loginAttemptsDetectionEnabled: true,
        maxLoginAttempts: 10,
        loginAttemptsResetTime: 43200,
        accountBlockedDuration: 7200,
      });

      await page.reload();
      await expect(page.getByRole('switch', { name: 'Brute force detection', exact: true })).toBeChecked();
      await expect(page.getByRole('spinbutton', { name: 'Login attempts reset time' })).toHaveValue('12');
      await expect(page.getByRole('combobox', { name: 'Login attempts reset time unit' })).toHaveText('Hours');
      await expect(page.getByRole('spinbutton', { name: 'Account blocked duration' })).toHaveValue('2');
    });
  });
});
