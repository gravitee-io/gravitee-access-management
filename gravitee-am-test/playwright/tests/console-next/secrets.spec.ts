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

/** The client secret expiry of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: domain client secrets', () => {
  const secretsUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/secrets`;

  test('enables the expiry with a duration in weeks, then shows it after a reload', async ({ page, adminToken, testDomain }) => {
    await page.goto(secretsUrl(testDomain.hrid));

    await page.getByRole('switch', { name: 'Enable client secret expiry' }).click();
    await expect(page.getByRole('spinbutton', { name: 'Expiry time' })).toHaveValue('90');
    await page.getByRole('combobox', { name: 'Expiry time unit' }).click();
    await page.getByRole('option', { name: 'Weeks' }).click();
    await page.getByRole('spinbutton', { name: 'Expiry time' }).fill('2');
    const patchRequest = page.waitForRequest(
      (request) => request.method() === 'PATCH' && request.url().endsWith(`/domains/${testDomain.id}`),
    );
    const patchResponse = page.waitForResponse(
      (response) => response.request().method() === 'PATCH' && response.url().endsWith(`/domains/${testDomain.id}`),
    );
    await page.getByRole('button', { name: 'Save' }).click();

    expect((await patchRequest).postDataJSON()).toEqual({ secretSettings: { enabled: true, expiryTimeSeconds: 1_209_600 } });
    expect((await patchResponse).ok()).toBe(true);
    const saved = await getDomain(testDomain.id, adminToken);
    expect(saved.secretExpirationSettings).toMatchObject({ enabled: true, expiryTimeSeconds: 1_209_600 });

    await page.reload();
    await expect(page.getByRole('spinbutton', { name: 'Expiry time' })).toHaveValue('2');
    await expect(page.getByRole('combobox', { name: 'Expiry time unit' })).toHaveText('Weeks');
  });

  test('blocks the save while the duration is below 1', async ({ page, testDomain }) => {
    await page.goto(secretsUrl(testDomain.hrid));

    await page.getByRole('switch', { name: 'Enable client secret expiry' }).click();
    await page.getByRole('spinbutton', { name: 'Expiry time' }).fill('0');

    await expect(page.getByText('Duration must be greater than 0.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});
