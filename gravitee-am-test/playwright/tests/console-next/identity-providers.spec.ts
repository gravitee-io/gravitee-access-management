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
import { createIdp, defaultIdpId, getAllIdps, getIdp } from '@management-commands/idp-management-commands';

const inlineIdpBody = (name: string) => ({
  external: false,
  type: 'inline-am-idp',
  domainWhitelist: [],
  name,
  configuration: JSON.stringify({ users: [] }),
});

/** The identity providers of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: identity providers', () => {
  const providersUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/providers`;

  test('opens the user mappers of the system provider, which has no settings tab', async ({ page, testDomain }) => {
    await page.goto(providersUrl(testDomain.hrid));

    await page.getByRole('table').getByRole('link', { name: 'Default Identity Provider' }).click();

    await expect(page).toHaveURL(new RegExp(`/providers/${defaultIdpId(testDomain.id)}/mappers$`));
    await expect(page.getByRole('tab', { name: 'User mappers' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Settings' })).toHaveCount(0);
  });

  test('saves a new user mapper on the provider', async ({ page, adminToken, testDomain }) => {
    const idp = await createIdp(testDomain.id, adminToken, inlineIdpBody('Inline mapper test'));
    await page.goto(`${providersUrl(testDomain.hrid)}/${idp.id}/mappers`);

    await page.getByRole('button', { name: 'Add mapper' }).click();
    await page.getByRole('textbox', { name: 'User profile attribute' }).fill('nickname');
    await page.getByRole('textbox', { name: 'Raw attribute' }).fill("{#profile['nick']}");
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('button', { name: 'Save' })).toBeDisabled();

    const saved = await getIdp(testDomain.id, adminToken, idp.id);
    expect(saved.mappers).toEqual({ nickname: "{#profile['nick']}" });
  });

  test('deletes a provider from the list after the confirmation', async ({ page, adminToken, testDomain }) => {
    const idp = await createIdp(testDomain.id, adminToken, inlineIdpBody('Inline delete test'));
    await page.goto(providersUrl(testDomain.hrid));

    await page.getByRole('button', { name: 'Delete Inline delete test' }).click();
    await page.getByRole('dialog', { name: 'Delete provider' }).getByRole('button', { name: 'Delete' }).click();

    const table = page.getByRole('table');
    await expect(table.getByRole('link', { name: 'Default Identity Provider' })).toBeVisible();
    await expect(table.getByRole('link', { name: 'Inline delete test' })).toHaveCount(0);
    const remaining = await getAllIdps(testDomain.id, adminToken);
    expect(remaining.map((provider) => provider.id)).not.toContain(idp.id);
  });
});
