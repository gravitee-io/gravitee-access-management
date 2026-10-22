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
import { getAllIdps, getIdp } from '@management-commands/idp-management-commands';
import { createPasswordPolicy, getAllPasswordPolicies } from '@management-commands/password-policy-management-commands';

/** The password policies of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: domain password policies', () => {
  const policiesUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/password-policies`;

  test('creates a policy linked to the default identity provider', async ({ page, adminToken, testDomain }) => {
    const [idp] = await getAllIdps(testDomain.id, adminToken);
    await page.goto(`${policiesUrl(testDomain.hrid)}/new`);

    await page.getByRole('textbox', { name: 'Name' }).fill('pw-next-strong');
    await page.getByRole('spinbutton', { name: 'Minimum length' }).fill('12');
    await page.getByRole('switch', { name: 'Password must include numbers' }).click();
    await page.getByRole('button', { name: 'Select identity providers' }).click();
    const dialog = page.getByRole('dialog', { name: 'Select identity providers' });
    await dialog.getByRole('checkbox', { name: idp.name }).click();
    await dialog.getByRole('button', { name: 'Apply' }).click();
    const createRequest = page.waitForRequest(
      (request) => request.method() === 'POST' && request.url().endsWith(`/domains/${testDomain.id}/password-policies`),
    );
    const assignResponse = page.waitForResponse(
      (response) => response.request().method() === 'PUT' && response.url().endsWith(`/identities/${idp.id}/password-policy`),
    );
    await page.getByRole('button', { name: 'Create policy' }).click();

    expect((await createRequest).postDataJSON()).toEqual({ name: 'pw-next-strong', minLength: 12, includeNumbers: true });
    expect((await assignResponse).ok()).toBe(true);
    const [policy] = await getAllPasswordPolicies(testDomain.id, adminToken);
    expect(policy).toMatchObject({ name: 'pw-next-strong', idpsNames: [idp.name] });
    expect((await assignResponse).request().postDataJSON()).toEqual({ passwordPolicy: policy.id });
    await expect(page).toHaveURL(new RegExp(`/password-policies/${policy.id}$`));
    await expect(page.getByRole('heading', { level: 1, name: 'pw-next-strong' })).toBeVisible();
    expect((await getIdp(testDomain.id, adminToken, idp.id)).passwordPolicy).toBe(policy.id);
  });

  test('sets a policy as the default one, then deletes another after the confirmation', async ({ page, adminToken, testDomain }) => {
    const first = await createPasswordPolicy(testDomain.id, adminToken, { name: 'pw-next-first' });
    const second = await createPasswordPolicy(testDomain.id, adminToken, { name: 'pw-next-second' });
    await page.goto(policiesUrl(testDomain.hrid));

    await page.getByRole('button', { name: 'Actions for pw-next-second' }).click();
    const defaultResponse = page.waitForResponse(
      (response) => response.request().method() === 'POST' && response.url().endsWith(`/password-policies/${second.id}/default`),
    );
    await page.getByRole('menuitem', { name: 'Set as default' }).click();
    expect((await defaultResponse).ok()).toBe(true);
    await expect(page.getByRole('row', { name: /pw-next-second/ }).getByText('Default')).toBeVisible();

    await page.getByRole('button', { name: 'Actions for pw-next-first' }).click();
    await page.getByRole('menuitem', { name: 'Delete' }).click();
    const deleteResponse = page.waitForResponse(
      (response) => response.request().method() === 'DELETE' && response.url().endsWith(`/password-policies/${first.id}`),
    );
    await page.getByRole('dialog', { name: 'Delete Password Policy' }).getByRole('button', { name: 'Delete' }).click();
    expect((await deleteResponse).ok()).toBe(true);

    await expect(page.getByRole('row', { name: /pw-next-first/ })).toHaveCount(0);
    const remaining = await getAllPasswordPolicies(testDomain.id, adminToken);
    expect(remaining.map((policy) => policy.name)).toEqual(['pw-next-second']);
  });
});
