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
import { linkJira } from '../../utils/jira';
import { DomainFactorsPage } from '../../pages/domain-factors.page';
import { createFactor } from '@management-commands/factor-management-commands';
import { uniqueTestName as uniqueName } from '../../utils/fixture-helpers';

// Follows pattern: security/certificates/crud.spec.ts (admin UI CRUD)

test.describe('MFA Factor CRUD', () => {
  test('AM-2226: display empty factors list and navigate to creation wizard', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-2226');

    const factorsPage = new DomainFactorsPage(page);
    await factorsPage.navigateTo(testDomain.id);

    // Positive anchor: confirm the page container rendered before asserting empty state
    await expect(page.locator('.gv-page-container')).toBeVisible();

    // Fresh domain has no factors — empty state should be shown
    await expect(factorsPage.emptyState).toBeVisible();

    // Click FAB to navigate to creation wizard
    await factorsPage.addButton.click();
    await factorsPage.waitForReady();

    // Stepper should be visible with "New factor" heading
    await expect(factorsPage.stepper).toBeVisible();
    await expect(page.locator('h1').filter({ hasText: /new factor/i })).toBeVisible();
  });

  test('AM-2226: create an OTP factor via the wizard', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-2226');

    const factorsPage = new DomainFactorsPage(page);
    await factorsPage.navigateTo(testDomain.id);

    // Navigate to creation wizard
    await expect(factorsPage.addButton).toBeVisible();
    await factorsPage.addButton.click();
    await factorsPage.waitForReady();
    await expect(factorsPage.stepper).toBeVisible();

    // Step 1: select OTP factor type — verify radio is visible (proves plugin is deployed)
    const otpCard = page.getByTestId('factorType-otp-am-factor');
    await expect(otpCard.getByRole('radio')).toBeVisible();
    await factorsPage.selectFactorType('otp-am-factor');
    await expect(factorsPage.nextButton).toBeEnabled();
    await factorsPage.nextButton.click();

    // Step 2: factor config form with Name field + JSON schema config
    await expect(factorsPage.createButton).toBeVisible();

    // Fill required "Name" field — wait for input to be visible before filling
    const factorName = uniqueName('pw-otp');
    await expect(factorsPage.nameInput).toBeVisible();
    await factorsPage.nameInput.fill(factorName);

    // Wait for Create button to become enabled after form validates
    await expect(factorsPage.createButton).toBeEnabled();
    await factorsPage.createButton.click();

    // After creation, the app redirects to the factor detail page
    await factorsPage.waitForReady();
    await expect(factorsPage.factorHeading).toContainText(factorName);
  });

  test('AM-2237: verify created factor appears in list', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2237');

    // Create a factor via API first
    const factorName = uniqueName('pw-otp-factor');
    await createFactor(testDomain.id, adminToken, {
      type: 'otp-am-factor',
      factorType: 'TOTP',
      name: factorName,
      configuration: JSON.stringify({ issuer: 'Gravitee.io' }),
    });

    const factorsPage = new DomainFactorsPage(page);
    await factorsPage.navigateTo(testDomain.id);

    // Factor should be visible in the list
    await expect(factorsPage.factorRows.first()).toBeVisible();
    const row = factorsPage.factorRow(new RegExp(factorName));
    await expect(row).toBeVisible();
  });

  test('AM-2237: update factor name via detail page', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2237');

    // Create a factor via API
    const factorName = uniqueName('pw-otp-update');
    const factor = await createFactor(testDomain.id, adminToken, {
      type: 'otp-am-factor',
      factorType: 'TOTP',
      name: factorName,
      configuration: JSON.stringify({ issuer: 'Gravitee.io' }),
    });

    // Navigate to factor detail
    const factorsPage = new DomainFactorsPage(page);
    await factorsPage.navigateToFactor(testDomain.id, factor.id);

    // Verify factor heading shows the name
    await expect(factorsPage.factorHeading).toContainText(factorName);

    // Update the name — select all + type over (Angular ngModel + ngIf safe)
    const updatedName = uniqueName('pw-otp-renamed');
    await expect(factorsPage.nameInput).toBeVisible();
    await factorsPage.nameInput.click({ clickCount: 3 });
    await factorsPage.nameInput.pressSequentially(updatedName);

    // Save
    await expect(factorsPage.saveButton).toBeVisible();
    await expect(factorsPage.saveButton).toBeEnabled();
    await factorsPage.saveButton.click();
    await factorsPage.expectSnackbar(/Factor updated/i);
  });

  test('AM-2235: delete factor via detail page', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2235');

    // Create a disposable factor via API
    const factorName = uniqueName('pw-otp-delete');
    const factor = await createFactor(testDomain.id, adminToken, {
      type: 'otp-am-factor',
      factorType: 'TOTP',
      name: factorName,
      configuration: JSON.stringify({ issuer: 'Gravitee.io' }),
    });

    // Navigate to factor detail
    const factorsPage = new DomainFactorsPage(page);
    await factorsPage.navigateToFactor(testDomain.id, factor.id);

    // Verify we're on the right page
    await expect(factorsPage.factorHeading).toContainText(factorName);

    // Click DELETE and confirm dialog
    await expect(factorsPage.factorDeleteButton).toBeVisible();
    await factorsPage.factorDeleteButton.click();
    await factorsPage.confirmDialog();

    // After deletion, app redirects back to the factors list
    await expect(page).toHaveURL(/\/factors$/);
    await factorsPage.waitForReady();

    // Verify the domain has no factors left (empty state proves deletion, not vacuous table absence)
    await expect(factorsPage.emptyState).toBeVisible();
  });
});
