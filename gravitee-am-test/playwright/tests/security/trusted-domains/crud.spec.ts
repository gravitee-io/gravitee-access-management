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
import { TrustedDomainListPage } from '../../../pages/trusted-domain-list.page';
import { TrustedDomainDetailPage } from '../../../pages/trusted-domain-detail.page';
import { createKeyMaterial, jwkSetOf, patchDomainRaw } from '../../../utils/token-exchange-helpers';

const SHARED_NAME = 'shared.example';

test.describe('Trusted Domains CRUD', () => {
  async function enableSpiffe(domainId: string, adminToken: string) {
    await patchDomainRaw(domainId, adminToken, {
      oidc: {
        workloadIdentitySettings: { enabled: true },
        keyRetrievalSettings: { allowPrivateIpAddress: true, allowUnsecuredHttpUri: true },
      },
    }).expect(200);
  }

  test('empty state when nothing is trusted yet', async ({ page, testDomain }) => {
    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);

    await expect(listPage.emptyState).toBeVisible();
    await expect(listPage.emptyState).toContainText(/trusted domains will appear here/i);
  });

  test('creation defaults to token exchange and shows only the chosen kind settings', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);

    await expect(detailPage.kindChoice('TOKEN_EXCHANGE')).toBeVisible();
    await expect(detailPage.kindChoice('SPIFFE')).toBeVisible();
    await expect(detailPage.selectedKind).toHaveText(/token exchange/i);
    await expect(detailPage.issuerUrlInput).toBeVisible();
    await expect(detailPage.allowedAlgorithmsInput).toHaveCount(0);

    await detailPage.chooseKind('SPIFFE');
    await expect(detailPage.nameInput).toBeVisible();
    await expect(detailPage.issuerUrlInput).toHaveCount(0);
    await expect(detailPage.allowedAlgorithmsInput).toBeVisible();

    await detailPage.chooseKind('TOKEN_EXCHANGE');
    await expect(detailPage.issuerUrlInput).toBeVisible();
    await expect(detailPage.allowedAlgorithmsInput).toHaveCount(0);
  });

  test('create a token-exchange trusted domain with PEM key material and a scope mapping', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('TOKEN_EXCHANGE');

    await detailPage.issuerUrlInput.fill('https://external-idp.example.com');
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);
    await detailPage.addScopeMapping('external:read', 'openid');

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.trustDomainRows).toHaveCount(1);
    await expect(listPage.kindOf(0)).toHaveText(/token exchange/i);
    await expect(listPage.rowByName('https-external-idp.example.com')).toHaveCount(1);
  });

  test('create a token-exchange trusted domain with JWKS URL key material', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('TOKEN_EXCHANGE');

    await detailPage.issuerUrlInput.fill('https://accounts.google.com');
    await detailPage.selectKeySource(/JWKS URL/i);
    await detailPage.jwksUrlInput.fill('https://www.googleapis.com/oauth2/v3/certs');

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);
  });

  test('create a SPIFFE trusted domain with an inline JWK set', async ({ page, testDomain, adminToken }) => {
    await enableSpiffe(testDomain.id, adminToken);

    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('SPIFFE');

    await detailPage.nameInput.fill('spire.example');
    await detailPage.selectKeySource(/JWK Set/i);
    await detailPage.jwkSetTextarea.fill(JSON.stringify(jwkSetOf(createKeyMaterial().certificatePem)));

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.kindOf(0)).toHaveText(/spiffe/i);
  });

  test('the list shows both kinds side by side when they share a name', async ({ page, testDomain, adminToken }) => {
    await enableSpiffe(testDomain.id, adminToken);

    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('SPIFFE');
    await detailPage.nameInput.fill(SHARED_NAME);
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('TOKEN_EXCHANGE');
    await detailPage.issuerUrlInput.fill('https://shared.example');
    await detailPage.nameInput.fill(SHARED_NAME);
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.rowByName(SHARED_NAME)).toHaveCount(2);
    const kinds = await listPage.trustDomainRows.locator('[data-testid="trustDomainKind"]').allTextContents();
    expect(kinds.map((k) => k.trim()).sort()).toEqual(['SPIFFE', 'Token Exchange']);
  });

  test('a detail page is addressed by identifier and can be linked to directly', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    const certificatePem = createKeyMaterial().certificatePem;
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('TOKEN_EXCHANGE');
    await detailPage.issuerUrlInput.fill('https://linkable.example.com');
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const url = page.url();
    const trustDomainId = url.substring(url.lastIndexOf('/') + 1);
    expect(trustDomainId).toMatch(/^[0-9a-f-]{36}$/);

    await detailPage.navigateToEdit(testDomain.id, trustDomainId);
    await expect(detailPage.issuerUrlInput).toHaveValue('https://linkable.example.com');
    await expect(detailPage.kindBadge).toHaveText(/token exchange/i);
    await expect(detailPage.keySourceSelect).toHaveText(/PEM/i);
    const shownPem = (await detailPage.pemCertTextarea.inputValue()).replace(/\r\n/g, '\n').trim();
    expect(shownPem).toBe(certificatePem.replace(/\r\n/g, '\n').trim());
  });

  test('edit scope mappings on an existing trusted domain', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('TOKEN_EXCHANGE');
    await detailPage.issuerUrlInput.fill('https://edit-test.example.com');
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await listPage.clickEditTrustDomain(0);

    await detailPage.addScopeMapping('external:write', 'profile');
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/updated/i);

    await page.reload();
    await detailPage.waitForReady();
    await expect(detailPage.scopeMappingRows.first()).toBeVisible();
  });

  test('configure user binding on a token-exchange trusted domain', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('TOKEN_EXCHANGE');
    await detailPage.issuerUrlInput.fill('https://user-binding-test.example.com');
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);

    await detailPage.userBindingToggle.click();
    await detailPage.waitForReady();
    await expect(detailPage.userAttributeInput).toBeVisible();
    await detailPage.addUserBinding('email', 'email');

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);
  });

  test('delete a trusted domain from the list', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.chooseKind('TOKEN_EXCHANGE');
    await detailPage.issuerUrlInput.fill('https://delete-test.example.com');
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.rowByName('https-delete-test.example.com')).toHaveCount(1);
    const countBefore = await listPage.getTrustDomainCount();

    await listPage.clickDeleteTrustDomain(0);
    await listPage.confirmDialog();
    await listPage.expectSnackbar(/deleted/i);

    await expect(listPage.trustDomainRows).toHaveCount(countBefore - 1);
    await expect(listPage.rowByName('https-delete-test.example.com')).toHaveCount(0);
  });

  test('token exchange keeps its settings tab, drops trusted issuers and links to trusted domains', async ({ page, testDomain }) => {
    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);

    await expect(tePage.settingsTab).toBeVisible();
    await expect(page.locator('a[mat-tab-link]').filter({ hasText: /trusted issuers/i })).toHaveCount(0);

    await tePage.trustedDomainsLink.click();
    await page.waitForURL(/\/settings\/trust-domains$/);
    await tePage.waitForReady();
  });
});
