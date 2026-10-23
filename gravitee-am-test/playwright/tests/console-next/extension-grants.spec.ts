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
import { createExtensionGrant, getExtensionGrant } from '@management-commands/extension-grant-commands';

const JWT_BEARER = 'jwtbearer-am-extension-grant';
const JWT_BEARER_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:jwt-bearer';

const jwtBearerBody = (name: string) => ({
  type: JWT_BEARER,
  name,
  grantType: JWT_BEARER_GRANT_TYPE,
  configuration: JSON.stringify({ publicKeyResolver: 'GIVEN_KEY', publicKey: 'ssh-rsa AAAA' }),
});

/** The extension grants of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: extension grants', () => {
  const extensionGrantsUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/extensionGrants`;

  test('creates a JWT Bearer extension grant with the wizard', async ({ page, adminToken, testDomain }) => {
    await page.goto(extensionGrantsUrl(testDomain.hrid));

    await page.getByRole('link', { name: 'Create extension grant' }).click();
    await page.getByRole('radio', { name: 'JWT Bearer' }).click();
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill('pw-next-jwt');
    await expect(page.getByRole('textbox', { name: 'Grant type' })).toHaveValue(JWT_BEARER_GRANT_TYPE);
    await page.getByRole('textbox', { name: /Resolver parameter/ }).fill('ssh-rsa AAAA');
    const createRequest = page.waitForRequest((request) => request.method() === 'POST' && request.url().endsWith('/extensionGrants'));
    await page.getByRole('button', { name: 'Create' }).click();

    const body = (await createRequest).postDataJSON();
    expect(body).toMatchObject({ name: 'pw-next-jwt', type: JWT_BEARER, grantType: JWT_BEARER_GRANT_TYPE });
    // The Angular form renders neither the untyped grantType property nor an untouched array, so neither key is sent.
    expect(JSON.parse(body.configuration)).toEqual({ publicKeyResolver: 'GIVEN_KEY', publicKey: 'ssh-rsa AAAA' });
    await expect(page.getByRole('heading', { level: 1, name: 'pw-next-jwt' })).toBeVisible();
    const created = await getExtensionGrant(testDomain.id, adminToken, page.url().split('/').pop());
    expect(created).toMatchObject({ name: 'pw-next-jwt', type: JWT_BEARER, grantType: JWT_BEARER_GRANT_TYPE });
  });

  test('saves the user settings of an extension grant on its page', async ({ page, adminToken, testDomain }) => {
    const created = await createExtensionGrant(testDomain.id, adminToken, jwtBearerBody('pw-next-save'));
    await page.goto(extensionGrantsUrl(testDomain.hrid));

    await page.getByRole('table').getByRole('link', { name: 'pw-next-save' }).click();
    await expect(page.getByRole('button', { name: 'Save' })).toBeDisabled();
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill('pw-next-saved');
    await page.getByRole('switch', { name: 'Check user existence' }).click();
    const saveResponse = page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().endsWith(created.id));
    await page.getByRole('button', { name: 'Save' }).click();
    expect((await saveResponse).ok()).toBe(true);

    const saved: any = await getExtensionGrant(testDomain.id, adminToken, created.id);
    expect(saved).toMatchObject({ name: 'pw-next-saved', userExists: true, grantType: JWT_BEARER_GRANT_TYPE });
    expect(JSON.parse(saved.configuration)).toEqual({ publicKeyResolver: 'GIVEN_KEY', publicKey: 'ssh-rsa AAAA' });
  });

  test('deletes an extension grant after the confirmation', async ({ page, testDomain, adminToken }) => {
    const created = await createExtensionGrant(testDomain.id, adminToken, jwtBearerBody('pw-next-delete'));
    await page.goto(`${extensionGrantsUrl(testDomain.hrid)}/${created.id}`);

    await page.getByRole('button', { name: 'Delete' }).click();
    const dialog = page.getByRole('dialog', { name: 'Delete extension grant' });
    await dialog.getByRole('button', { name: 'Delete extension grant' }).click();

    await expect(page).toHaveURL(/\/settings\/extensionGrants$/);
    await expect(page.getByText('No extension grants yet')).toBeVisible();
  });
});
