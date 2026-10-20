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
import { createFactor, getFactor } from '@management-commands/factor-management-commands';

const otpFactorBody = (name: string) => ({
  type: 'otp-am-factor',
  factorType: 'TOTP',
  name,
  configuration: JSON.stringify({ issuer: 'Gravitee.io' }),
});

/** The factors of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: factors', () => {
  const factorsUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/factors`;

  test('creates an OTP factor with the wizard', async ({ page, adminToken, testDomain }) => {
    await page.goto(factorsUrl(testDomain.hrid));

    await page.getByRole('link', { name: 'Add factor' }).click();
    await page.getByRole('radio', { name: 'Generic OTP Factor' }).click();
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('textbox', { name: 'Name' }).fill('pw-next-otp');
    await expect(page.getByRole('textbox', { name: 'Factor type' })).toHaveValue('TOTP');
    const createRequest = page.waitForRequest((request) => request.method() === 'POST' && request.url().endsWith('/factors'));
    await page.getByRole('button', { name: 'Create' }).click();

    expect((await createRequest).postDataJSON()).toMatchObject({ name: 'pw-next-otp', type: 'otp-am-factor', factorType: 'TOTP' });
    await expect(page.getByRole('heading', { level: 1, name: 'pw-next-otp' })).toBeVisible();
    const created = await getFactor(testDomain.id, adminToken, page.url().split('/').pop());
    expect(created).toMatchObject({ name: 'pw-next-otp', type: 'otp-am-factor' });
  });

  test('renames a factor on its page', async ({ page, adminToken, testDomain }) => {
    const created = await createFactor(testDomain.id, adminToken, otpFactorBody('pw-next-rename'));
    await page.goto(factorsUrl(testDomain.hrid));

    await page.getByRole('table').getByRole('link', { name: 'pw-next-rename' }).click();
    await page.getByRole('textbox', { name: 'Name' }).fill('pw-next-renamed');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('button', { name: 'Save' })).toBeDisabled();

    const saved = await getFactor(testDomain.id, adminToken, created.id);
    expect(saved.name).toEqual('pw-next-renamed');
    expect(JSON.parse(saved.configuration)).toMatchObject({ issuer: 'Gravitee.io' });
  });

  test('deletes a factor after the confirmation', async ({ page, testDomain, adminToken }) => {
    const created = await createFactor(testDomain.id, adminToken, otpFactorBody('pw-next-delete'));
    await page.goto(`${factorsUrl(testDomain.hrid)}/${created.id}`);

    await page.getByRole('button', { name: 'Delete' }).click();
    const dialog = page.getByRole('dialog', { name: 'Delete factor' });
    await dialog.getByRole('button', { name: 'Delete' }).click();

    await expect(page).toHaveURL(new RegExp(`/settings/factors$`));
    await expect(page.getByText('No factors yet')).toBeVisible();
  });
});
