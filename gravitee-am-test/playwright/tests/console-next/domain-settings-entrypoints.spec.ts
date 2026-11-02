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

test.describe('React console: domain settings, entrypoints', () => {
  test('a virtual host is saved as the overriding entrypoint and shown after a reload', async ({ page, testDomain, adminToken }) => {
    const host = `${testDomain.hrid}.example.com`;

    await test.step('switch to virtual hosts mode, add a host, then save', async () => {
      await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains/${testDomain.hrid}/settings/entrypoints`);
      await page.getByRole('button', { name: 'Switch to virtual hosts mode' }).click();
      await page.getByRole('button', { name: 'Add virtual host' }).click();
      await page.getByRole('textbox', { name: 'Host' }).fill(host);
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText(`Domain ${testDomain.name} updated`)).toBeVisible();
    });

    await test.step('the domain keeps the virtual host after a reload', async () => {
      const updated = await getDomain(testDomain.id, adminToken);
      expect(updated.vhostMode).toBe(true);
      expect(updated.vhosts).toEqual([{ host, path: testDomain.path, overrideEntrypoint: true }]);

      await page.reload();
      await expect(page.getByRole('button', { name: 'Switch to context-path mode' })).toBeVisible();
      await expect(page.getByRole('textbox', { name: 'Host' })).toHaveValue(host);
    });
  });
});
