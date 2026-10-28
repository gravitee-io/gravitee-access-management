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
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueTestName } from '../../utils/fixture-helpers';

/** The domains list of the React console (gravitee-am-console). Runs in the console-next project only. */
test.describe('React console: domains list', () => {
  test('finds a domain created via API by search', async ({ page, testDomain }) => {
    await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains`);
    const table = page.getByRole('table');
    await expect(table).toBeVisible();

    await page.getByRole('searchbox', { name: 'Search domains' }).fill(testDomain.name);

    await expect(table.getByRole('link', { name: testDomain.name })).toBeVisible();
  });

  test('creates a domain and opens it', async ({ page, adminToken }) => {
    const name = uniqueTestName('pw-next-domain');
    await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains`);

    await page.getByRole('link', { name: 'Create domain' }).click();
    await page.getByRole('textbox', { name: 'Name' }).fill(name);
    const createRequest = page.waitForRequest((request) => request.method() === 'POST' && request.url().endsWith('/domains'));
    const createResponse = page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/domains'));
    await page.getByRole('button', { name: 'Create domain' }).click();

    expect((await createRequest).postDataJSON()).toEqual({ name, dataPlaneId: 'default' });
    const created = await (await createResponse).json();
    try {
      await expect(page).toHaveURL(new RegExp(`/domains/${created.hrid}/dashboard$`));
    } finally {
      await safeDeleteDomain(created.id, adminToken);
    }
  });
});
