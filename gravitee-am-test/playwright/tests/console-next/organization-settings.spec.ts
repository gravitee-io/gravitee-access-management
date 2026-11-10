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
import { uniqueTestName } from '../../utils/fixture-helpers';

const ORGANIZATION = `/environments/${process.env.AM_DEF_ENV_HRID}/organization`;

test.describe('React console: organization settings', () => {
  test('a sharding tag is given to an entrypoint, and both are deleted', async ({ page }) => {
    const tagName = uniqueTestName('tag');
    const entrypointName = uniqueTestName('entrypoint');

    await test.step('create a sharding tag', async () => {
      await page.goto(`${ORGANIZATION}/tags/new`);
      await page.getByRole('textbox', { name: 'Name' }).fill(tagName);
      await page.getByRole('button', { name: 'Create' }).click();
      await expect(page.getByText(`Sharding Tag ${tagName} created`)).toBeVisible();
    });

    await test.step('create an entrypoint and give it the tag', async () => {
      await page.goto(`${ORGANIZATION}/entrypoints/new`);
      await page.getByRole('textbox', { name: 'Name' }).fill(entrypointName);
      await page.getByRole('textbox', { name: 'Entrypoint url' }).fill('https://entrypoint.example.com');
      await page.getByRole('button', { name: 'Create' }).click();
      await expect(page.getByText(`Entrypoint ${entrypointName} created`)).toBeVisible();

      await page.getByRole('combobox', { name: 'Sharding tags' }).click();
      await page.getByRole('option', { name: new RegExp(tagName) }).click();
      const saved = page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes('/entrypoints/'));
      await page.getByRole('button', { name: 'Save' }).click();
      expect((await saved).ok()).toBe(true);
    });

    await test.step('the entrypoint keeps the tag after a reload', async () => {
      await page.reload();
      await expect(page.getByText(tagName).first()).toBeVisible();
    });

    await test.step('delete the entrypoint, then the tag', async () => {
      await page.goto(`${ORGANIZATION}/entrypoints`);
      await page.getByRole('button', { name: `Delete ${entrypointName}` }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
      await expect(page.getByText('Entrypoint deleted')).toBeVisible();

      await page.goto(`${ORGANIZATION}/tags`);
      await page.getByRole('button', { name: `Delete ${tagName}` }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
      await expect(page.getByText('Sharding tag deleted')).toBeVisible();
    });
  });

  test('the console login form is created and deleted', async ({ page }) => {
    await page.goto(`${ORGANIZATION}/forms/form?template=LOGIN`);

    await test.step('enable and save a custom form', async () => {
      await page.getByRole('switch', { name: 'Enable custom login form.' }).click();
      const saved = page.waitForResponse((response) => response.request().method() === 'POST' && response.url().endsWith('/forms'));
      await page.getByRole('button', { name: 'Save' }).click();
      expect((await saved).ok()).toBe(true);
      await expect(page.getByText('Form created')).toBeVisible();
    });

    await test.step('delete the form, so the default page applies again', async () => {
      await page.getByRole('button', { name: 'Delete' }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
      await expect(page.getByText('Form deleted')).toBeVisible();
    });
  });
});
