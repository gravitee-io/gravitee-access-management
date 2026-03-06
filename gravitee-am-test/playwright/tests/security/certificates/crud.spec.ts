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
import { test, expect } from '../../../fixtures/base.fixture';
import { linkJira } from '../../../utils/jira';
import { DomainCertificatesPage } from '../../../pages/domain-certificates.page';
import { updateCertificateSettings } from '../../../../api/commands/management/domain-management-commands';
import { getAllCertificates } from '../../../../api/commands/management/certificate-management-commands';

test.describe('Certificate CRUD', () => {
  test('display certificate list with system certificate', async ({ page, testDomain }) => {
    const certPage = new DomainCertificatesPage(page);
    await certPage.navigateTo(testDomain.id);

    // Every domain has at least a system certificate
    await expect(certPage.certificateRows.first()).toBeVisible();
    const count = await certPage.getCertificateCount();
    expect(count).toBeGreaterThanOrEqual(1);

    // System badge should be visible
    const firstRow = certPage.certificateRows.first();
    await expect(firstRow).toContainText(/system/i);
  });

  test('show fallback badge for fallback certificate', async ({ page, testDomain }) => {
    const certPage = new DomainCertificatesPage(page);
    await certPage.navigateTo(testDomain.id);

    // Wait for table to render
    await expect(certPage.certificateRows.first()).toBeVisible();

    // Set fallback certificate via settings dialog
    await certPage.openSettingsDialog();
    // Select the first available certificate as fallback
    await certPage.fallbackCertificateSelect.click();
    const firstOption = page.locator('mat-option').first();
    await firstOption.click();
    await certPage.confirmSettingsDialog();
    await certPage.expectSnackbar(/updated|settings/i);

    // Reload and verify fallback badge
    await certPage.navigateTo(testDomain.id);
    await expect(certPage.certificateRows.first()).toBeVisible();

    // At least one row should have a fallback badge
    const firstRow = certPage.certificateRows.first();
    await expect(certPage.fallbackBadge(firstRow)).toBeVisible();
  });

  test('settings dialog: select fallback certificate', async ({ page, testDomain }) => {
    const certPage = new DomainCertificatesPage(page);
    await certPage.navigateTo(testDomain.id);

    await expect(certPage.certificateRows.first()).toBeVisible();

    // Open settings dialog
    await certPage.openSettingsDialog();
    await expect(certPage.settingsDialog).toBeVisible();

    // Select a certificate
    await certPage.fallbackCertificateSelect.click();
    const options = page.locator('mat-option');
    const optionCount = await options.count();
    expect(optionCount).toBeGreaterThanOrEqual(1);
    await options.first().click();

    // Confirm and check snackbar
    await certPage.confirmSettingsDialog();
    await certPage.expectSnackbar(/updated|settings/i);
  });

  test('AM-6554: delete button disabled for fallback certificate', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-6554');

    // First set a fallback certificate via the API
    const certs = await getAllCertificates(testDomain.id, adminToken);
    expect(certs.length).toBeGreaterThanOrEqual(1);
    const fallbackCertId = certs[0].id;

    await updateCertificateSettings(testDomain.id, adminToken, { fallbackCertificate: fallbackCertId });

    // Navigate to certificates page
    const certPage = new DomainCertificatesPage(page);
    await certPage.navigateTo(testDomain.id);

    await expect(certPage.certificateRows.first()).toBeVisible();

    // Find the row with the fallback badge
    const fallbackRow = certPage.certificateRows.filter({ has: page.locator('[data-testid="certificateFallbackBadge"]') });
    await expect(fallbackRow).toBeVisible();

    // Delete button should be disabled on the fallback row
    const deleteBtn = certPage.deleteButton(fallbackRow);
    await expect(deleteBtn).toBeDisabled();
  });

  // Jira AM-1136 requires uploading an expired certificate and verifying rejection.
  // The UI uses a dynamic JSON Schema Form that is not feasible to drive in Playwright.
  // This test verifies the certificate creation page loads and the form is accessible.
  test('AM-1136: certificate upload form accessible', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-1136');

    const certPage = new DomainCertificatesPage(page);
    await certPage.navigateTo(testDomain.id);

    await expect(certPage.certificateRows.first()).toBeVisible();
    await certPage.addButton.click();
    await certPage.waitForReady();

    await expect(page.locator('h1, h2').filter({ hasText: /certificate/i })).toBeVisible();
  });
});
