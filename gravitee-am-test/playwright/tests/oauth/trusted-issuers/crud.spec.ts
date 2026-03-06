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
import { DomainTokenExchangePage } from '../../../pages/domain-token-exchange.page';
import { TrustedIssuerListPage } from '../../../pages/trusted-issuer-list.page';
import { TrustedIssuerDetailPage } from '../../../pages/trusted-issuer-detail.page';
import { createKeyMaterial } from '../../../utils/token-exchange-helpers';

test.describe('Trusted Issuers CRUD', () => {
  /** Enable token exchange on the domain so the Trusted Issuers tab is accessible. */
  async function enableTokenExchange(page: import('@playwright/test').Page, domainId: string) {
    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(domainId);
    await tePage.toggleEnable();
    await tePage.saveButton.click();
    await tePage.expectSnackbar(/saved|updated/i);
  }

  test('empty state when no issuers configured', async ({ page, testDomain }) => {
    await enableTokenExchange(page, testDomain.id);

    // Navigate to trusted issuers list
    const listPage = new TrustedIssuerListPage(page);
    await listPage.navigateTo(testDomain.id);

    await expect(listPage.emptyState).toBeVisible();
    await expect(listPage.emptyState).toContainText(/no trusted issuers/i);
  });

  test('create issuer with PEM key method + scope mapping', async ({ page, testDomain }) => {
    await enableTokenExchange(page, testDomain.id);

    const detailPage = new TrustedIssuerDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);

    // Fill issuer URL
    await detailPage.issuerUrlInput.fill('https://external-idp.example.com');

    // Select PEM key method
    await detailPage.selectKeyMethod(/PEM/i);

    // Fill PEM certificate
    const keyMaterial = createKeyMaterial();
    await detailPage.pemCertTextarea.fill(keyMaterial.certificatePem);

    // Add a scope mapping
    await detailPage.addScopeMapping('external:read', 'openid');

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/saved|created/i);

    // Verify the issuer appears in the list
    const listPage = new TrustedIssuerListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.issuerRows.first()).toBeVisible();
    expect(await listPage.getIssuerCount()).toBeGreaterThanOrEqual(1);
  });

  test('create issuer with JWKS URL method', async ({ page, testDomain }) => {
    await enableTokenExchange(page, testDomain.id);

    const detailPage = new TrustedIssuerDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);

    await detailPage.issuerUrlInput.fill('https://accounts.google.com');
    await detailPage.selectKeyMethod(/JWKS/i);
    await detailPage.jwksUriInput.fill('https://www.googleapis.com/oauth2/v3/certs');

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/saved|created/i);
  });

  test('edit issuer scope mappings', async ({ page, testDomain }) => {
    await enableTokenExchange(page, testDomain.id);

    // Create an issuer first
    const detailPage = new TrustedIssuerDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    const keyMaterial = createKeyMaterial();
    await detailPage.issuerUrlInput.fill('https://edit-test.example.com');
    await detailPage.selectKeyMethod(/PEM/i);
    await detailPage.pemCertTextarea.fill(keyMaterial.certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/saved|created/i);

    // Navigate back to list and click edit on the issuer
    const listPage = new TrustedIssuerListPage(page);
    await listPage.navigateTo(testDomain.id);
    await listPage.clickEditIssuer(0);

    // Add a scope mapping
    await detailPage.addScopeMapping('external:write', 'profile');
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/saved|updated/i);

    // Verify the scope mapping persists after reload
    await detailPage.navigateToEdit(testDomain.id, 0);
    await expect(detailPage.scopeMappingRows.first()).toBeVisible();
  });

  test('configure user binding on issuer', async ({ page, testDomain }) => {
    await enableTokenExchange(page, testDomain.id);

    const detailPage = new TrustedIssuerDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);

    const keyMaterial = createKeyMaterial();
    await detailPage.issuerUrlInput.fill('https://user-binding-test.example.com');
    await detailPage.selectKeyMethod(/PEM/i);
    await detailPage.pemCertTextarea.fill(keyMaterial.certificatePem);

    // Enable user binding (fields are behind *ngIf)
    await detailPage.userBindingToggle.click();
    await detailPage.waitForReady();
    await expect(detailPage.userAttributeInput).toBeVisible();

    // Add user binding criterion
    await detailPage.addUserBinding('email', 'email');

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/saved|created/i);
  });

  test('delete issuer + confirm dialog', async ({ page, testDomain }) => {
    await enableTokenExchange(page, testDomain.id);

    // Create an issuer to delete
    const detailPage = new TrustedIssuerDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    const keyMaterial = createKeyMaterial();
    await detailPage.issuerUrlInput.fill('https://delete-test.example.com');
    await detailPage.selectKeyMethod(/PEM/i);
    await detailPage.pemCertTextarea.fill(keyMaterial.certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/saved|created/i);

    // Navigate back to list, verify issuer exists
    const listPage = new TrustedIssuerListPage(page);
    await listPage.navigateTo(testDomain.id);
    const countBefore = await listPage.getIssuerCount();
    expect(countBefore).toBeGreaterThanOrEqual(1);

    // Delete via list action
    await listPage.clickDeleteIssuer(0);
    await listPage.confirmDialog();
    await listPage.expectSnackbar(/deleted|removed/i);

    // Verify the issuer is gone
    await listPage.waitForReady();
    const countAfter = await listPage.getIssuerCount();
    expect(countAfter).toBe(countBefore - 1);
  });
});
