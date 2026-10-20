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
import { createCertificate, getAllCertificates, getCertificate } from '@management-commands/certificate-management-commands';
import { getDomain } from '@management-commands/domain-management-commands';
import { validForDays } from '../../utils/pkcs12-certificate-with-validity';

/** The certificates of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: certificates', () => {
  const certificatesUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/certificates`;

  test('sets the fallback certificate from the settings dialog', async ({ page, adminToken, testDomain }) => {
    const [system] = await getAllCertificates(testDomain.id, adminToken);
    await page.goto(certificatesUrl(testDomain.hrid));

    await expect(page.getByRole('table').getByRole('link', { name: system.name })).toBeVisible();
    await page
      .locator('header', { has: page.getByRole('heading', { name: 'Certificates' }) })
      .getByRole('button', { name: 'Settings' })
      .click();
    const dialog = page.getByRole('dialog', { name: 'Certificate settings' });
    await dialog.getByRole('combobox', { name: 'Fallback certificate' }).click();
    await page.getByRole('option', { name: system.name }).click();
    await dialog.getByRole('button', { name: 'Confirm' }).click();

    await expect(
      page
        .getByRole('table')
        .getByRole('row', { name: new RegExp(system.name) })
        .getByText('Fallback'),
    ).toBeVisible();
    const domain = await getDomain(testDomain.id, adminToken);
    expect(domain.certificateSettings?.fallbackCertificate).toEqual(system.id);
  });

  test('flags a certificate near expiry, then deletes it after the confirmation', async ({ page, adminToken, testDomain }) => {
    const created = await createCertificate(testDomain.id, adminToken, validForDays('pw-next-near-expiry', 1));
    await page.goto(certificatesUrl(testDomain.hrid));

    const row = page.getByRole('table').getByRole('row', { name: /pw-next-near-expiry/ });
    await expect(row.getByLabel(/^Expiring in \d+ days$/)).toBeVisible();
    await row.getByRole('button', { name: 'Actions for pw-next-near-expiry' }).click();
    await page.getByRole('menuitem', { name: 'Delete' }).click();
    const dialog = page.getByRole('dialog', { name: 'Delete certificate' });
    await dialog.getByRole('button', { name: 'Delete' }).click();
    await expect(dialog).toBeHidden();

    await expect(page.getByRole('table').getByRole('link', { name: 'pw-next-near-expiry' })).toHaveCount(0);
    const remaining = await getAllCertificates(testDomain.id, adminToken);
    expect(remaining.map((certificate) => certificate.id)).not.toContain(created.id);
  });

  test('renames a certificate on its page', async ({ page, adminToken, testDomain }) => {
    const created = await createCertificate(testDomain.id, adminToken, validForDays('pw-next-rename', 30));
    await page.goto(`${certificatesUrl(testDomain.hrid)}/${created.id}`);

    await page.getByRole('textbox', { name: 'Name' }).fill('pw-next-renamed');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('button', { name: 'Save' })).toBeDisabled();

    const saved = await getCertificate(testDomain.id, adminToken, created.id);
    expect(saved.name).toEqual('pw-next-renamed');
  });
});
