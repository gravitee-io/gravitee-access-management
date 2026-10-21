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

/** The domain dashboard of the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: domain dashboard', () => {
  test('loads the 13 Angular widget queries and reloads them for a new time range', async ({ page, testDomain }) => {
    const analytics: URL[] = [];
    page.on('request', (request) => {
      if (request.url().includes(`/domains/${testDomain.id}/analytics?`)) analytics.push(new URL(request.url()));
    });

    await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains/${testDomain.hrid}/dashboard`);
    await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible();
    await expect.poll(() => analytics.length).toBe(13);
    expect(analytics.every((url) => url.searchParams.get('interval') === '3600000')).toBe(true);

    await page.getByRole('combobox', { name: 'Time range' }).click();
    await page.getByRole('option', { name: 'Last 90 days' }).click();

    await expect.poll(() => analytics.length).toBe(26);
    expect(analytics.slice(13).every((url) => url.searchParams.get('interval') === '86400000')).toBe(true);
  });
});
