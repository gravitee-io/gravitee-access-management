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

/** The domains list of the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: domains list', () => {
  test('finds a domain created via API by search', async ({ page, testDomain }) => {
    await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains`);
    const table = page.getByRole('table');
    await expect(table).toBeVisible();

    await page.getByRole('textbox', { name: 'Search domains' }).fill(testDomain.name);

    await expect(table.getByRole('link', { name: testDomain.name })).toBeVisible();
  });
});
