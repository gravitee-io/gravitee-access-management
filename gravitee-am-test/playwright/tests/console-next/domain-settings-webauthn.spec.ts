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

test.describe('React console: domain settings, WebAuthn', () => {
  test('the user verification and an attestation certificate are saved and shown after a reload', async ({
    page,
    testDomain,
    adminToken,
  }) => {
    const certificate = 'MIIBpw-console-next';

    await test.step('require user verification, add a packed certificate, then save', async () => {
      await page.goto(`/environments/${process.env.AM_DEF_ENV_HRID}/domains/${testDomain.hrid}/settings/webauthn`);
      await page.getByRole('combobox', { name: 'User verification' }).click();
      await page.getByRole('option', { name: 'required', exact: true }).click();
      await page.getByRole('combobox', { name: 'Type', exact: true }).click();
      await page.getByRole('option', { name: 'packed', exact: true }).click();
      await page.getByRole('textbox', { name: 'Certificate' }).fill(certificate);
      await page.getByRole('button', { name: 'Add', exact: true }).click();
      await page.getByRole('button', { name: 'Save' }).click();
      await expect(page.getByText('WebAuthn configuration updated')).toBeVisible();
    });

    await test.step('the domain keeps the settings after a reload', async () => {
      const { webAuthnSettings } = await getDomain(testDomain.id, adminToken);
      expect(webAuthnSettings).toMatchObject({ userVerification: 'required', certificates: { packed: certificate } });

      await page.reload();
      await expect(page.getByRole('combobox', { name: 'User verification' })).toHaveText('required');
      await expect(page.getByRole('row', { name: new RegExp(`^packed ${certificate}`) })).toBeVisible();
    });
  });
});
