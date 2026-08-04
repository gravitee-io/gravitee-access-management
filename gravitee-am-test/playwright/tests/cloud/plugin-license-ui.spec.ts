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

/**
 * AM-7240 / AM-7241 Group 6 — Console UI license gating for EE plugins.
 * Verifies the lock icon + upsell dialog genuinely pre-empt the backend 403 when
 * unlicensed, and that a licensed org can create the EE plugin normally end-to-end.
 *
 * Requires: --cloud stack with UI (local-stack.sh up --cloud --ui).
 * Signs in through Cockpit SSO against a Cockpit-provisioned organization — no DEFAULT admin.
 */
import { test, expect } from '../../fixtures/cloud-plugin-license.fixture';
import { linkJira } from '../../utils/jira';

test.describe('Console UI EE plugin license gating', () => {
  // Both tests mutate the same org-level license via cockpit — must not run concurrently.
  test.describe.configure({ mode: 'serial' });

  test.afterEach(async ({ licenseFixture }) => {
    // Reset to a clean no-license state so tests don't leak state to each other.
    await licenseFixture.clearOrgLicense();
  });

  test('AM-7240 / AM-7241: EE plugin create is blocked via the console UI with no org license', async ({
    signedInPage: page,
    testDomain,
    environmentHrid,
    licenseFixture,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7240', 'AM-7241');

    await licenseFixture.clearOrgLicense();

    // Wait for the license fetch to resolve before checking the DOM — GioLicenseService caches
    // it once per page load, so checking too early would race the initial render.
    await Promise.all([
      page.waitForResponse((res) => res.url().includes('/license') && res.request().method() === 'GET'),
      page.goto(`/environments/${environmentHrid}/domains/${testDomain.id}/settings/providers/new`),
    ]);

    await page.getByLabel('Filter providers...').fill('Azure');
    const azureCard = page.locator('mat-card', { hasText: 'Azure AD' });
    await expect(azureCard).toBeVisible();
    await expect(azureCard.locator('.plugin-license-lock')).toBeVisible();

    let identitiesRequestSeen = false;
    page.on('request', (req) => {
      if (req.method() === 'POST' && req.url().includes('/identities')) {
        identitiesRequestSeen = true;
      }
    });

    await azureCard.click();

    await expect(page.getByText('Unlock Gravitee Enterprise')).toBeVisible();
    await expect(page.getByText(/is part of Gravitee Enterprise/i)).toBeVisible();

    expect(identitiesRequestSeen).toBe(false);
  });

  test('AM-7240 / AM-7241: EE plugin create succeeds via the console UI with a universe org license', async ({
    signedInPage: page,
    testDomain,
    environmentHrid,
    licenseFixture,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7240', 'AM-7241');

    await licenseFixture.setUniverseLicense();

    await page.goto(`/environments/${environmentHrid}/domains/${testDomain.id}/settings/providers/new`);
    await page.reload();

    await page.getByLabel('Filter providers...').fill('Azure');
    const azureCard = page.locator('mat-card', { hasText: 'Azure AD' });
    await expect(azureCard).toBeVisible();
    await expect(azureCard.locator('.plugin-license-lock')).toBeHidden();

    await azureCard.click();
    await page.getByRole('button', { name: 'Next' }).click();

    await page.getByLabel(/^Name/).fill('pw-azure-license-test');
    await page.getByLabel(/^Tenant ID/).fill('t');
    await page.getByLabel(/^Client ID/).fill('t');
    await page.getByLabel(/^Client Secret/).fill('t');

    const [response] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'POST' && res.url().includes('/identities')),
      page.getByRole('button', { name: 'Create' }).click(),
    ]);

    expect(response.status()).toBeLessThan(300);
  });
});
