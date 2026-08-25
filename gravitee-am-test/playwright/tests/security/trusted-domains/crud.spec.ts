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
import { TrustedDomainKeyRetrievalPage } from '../../../pages/trusted-domain-key-retrieval.page';
import { createKeyMaterial, jwkSetOf, patchDomainRaw } from '../../../utils/token-exchange-helpers';

test.describe('Trusted Domains CRUD', () => {
  async function enableSpiffe(domainId: string, adminToken: string) {
    await patchDomainRaw(domainId, adminToken, {
      oidc: {
        workloadIdentitySettings: { enabled: true },
      },
      keyRetrievalSettings: { allowPrivateIpAddress: true, allowUnsecuredHttpUri: true },
    }).expect(200);
  }

  test('empty state when nothing is trusted yet', async ({ page, testDomain }) => {
    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);

    await expect(listPage.emptyState).toBeVisible();
    await expect(listPage.emptyState).toContainText(/trusted domains will appear here/i);
  });

  test('creation defaults to the trusted-issuer usage and reveals the matcher of each usage picked', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);

    await expect(detailPage.usageChoice('ISSUER')).toBeVisible();
    await expect(detailPage.usageChoice('SPIFFE')).toBeVisible();
    await expect(detailPage.usageChoice('ISSUER')).toContainText(/OIDC - Trusted Issuer/i);
    expect(await detailPage.isUsageSelected('ISSUER')).toBe(true);
    expect(await detailPage.isUsageSelected('SPIFFE')).toBe(false);
    await expect(detailPage.nameInput).toBeVisible();
    await expect(detailPage.issuerUrlInput).toBeVisible();
    await expect(detailPage.spiffeTrustDomainInput).toHaveCount(0);

    await detailPage.setUsage('SPIFFE', true);
    await expect(detailPage.spiffeTrustDomainInput).toBeVisible();
    await expect(detailPage.issuerUrlInput).toBeVisible();

    await detailPage.setUsage('ISSUER', false);
    await expect(detailPage.issuerUrlInput).toHaveCount(0);
    await expect(detailPage.spiffeTrustDomainInput).toBeVisible();
  });

  test('create a trusted-issuer trusted domain with PEM key material and a scope mapping', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);

    await detailPage.issuerUrlInput.fill('https://external-idp.example.com');
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);
    await detailPage.addScopeMapping('external:read', 'openid');

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.trustDomainRows).toHaveCount(1);
    await expect(listPage.usageOf(0)).toHaveText(/OIDC - Trusted Issuer/i);
    await expect(listPage.rowByName('https-external-idp.example.com')).toHaveCount(1);
  });

  test('create a trusted-issuer trusted domain with JWKS URL key material', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);

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
    await detailPage.setUsage('SPIFFE', true);
    await detailPage.setUsage('ISSUER', false);

    await detailPage.nameInput.fill('spire-prod');
    await detailPage.spiffeTrustDomainInput.fill('spire.example');
    await detailPage.selectKeySource(/JWK Set/i);
    await detailPage.jwkSetTextarea.fill(JSON.stringify(jwkSetOf(createKeyMaterial().certificatePem)));

    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.usageOf(0)).toHaveText(/^SPIFFE$/);
  });

  test('one trusted domain can serve both usages over the same key material', async ({ page, testDomain, adminToken }) => {
    await enableSpiffe(testDomain.id, adminToken);

    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
    await detailPage.setUsage('SPIFFE', true);
    await detailPage.nameInput.fill('acme-corp');
    await detailPage.spiffeTrustDomainInput.fill('acme.org');
    await detailPage.issuerUrlInput.fill('https://sso.acme.com');
    await detailPage.selectKeySource(/PEM/i);
    await detailPage.pemCertTextarea.fill(createKeyMaterial().certificatePem);
    await detailPage.saveButton.click();
    await detailPage.expectSnackbar(/created/i);

    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);
    await expect(listPage.trustDomainRows).toHaveCount(1);
    await expect(listPage.rowByName('acme-corp')).toHaveCount(1);
    await expect(listPage.usageOf(0)).toHaveText(/SPIFFE, OIDC - Trusted Issuer/i);
  });

  test('a detail page is addressed by identifier and can be linked to directly', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    const certificatePem = createKeyMaterial().certificatePem;
    await detailPage.navigateToNew(testDomain.id);
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
    await expect(detailPage.usageBadge).toHaveText(/OIDC - Trusted Issuer/i);
    await expect(detailPage.keySourceSelect).toHaveText(/PEM/i);
    const shownPem = (await detailPage.pemCertTextarea.inputValue()).replace(/\r\n/g, '\n').trim();
    expect(shownPem).toBe(certificatePem.replace(/\r\n/g, '\n').trim());
  });

  test('edit scope mappings on an existing trusted domain', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
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

  test('configure user binding on a trusted-issuer trusted domain', async ({ page, testDomain }) => {
    const detailPage = new TrustedDomainDetailPage(page);
    await detailPage.navigateToNew(testDomain.id);
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

  test('token exchange drops its tab bar and links to trusted domains once it is enabled', async ({ page, testDomain }) => {
    const tePage = new DomainTokenExchangePage(page);
    await tePage.navigateTo(testDomain.id);

    await expect(tePage.enableToggle).toBeVisible();
    await expect(tePage.trustedDomainsLink).toHaveCount(0);

    await tePage.toggleEnable();
    await expect(tePage.trustedDomainsLink).toBeVisible();

    await tePage.trustedDomainsLink.click();
    await page.waitForURL(/\/settings\/trust-domains\/domains$/);
    await tePage.waitForReady();
  });

  test('key retrieval settings sit on their own tab of the trusted domains page and survive a reload', async ({ page, testDomain }) => {
    const listPage = new TrustedDomainListPage(page);
    await listPage.navigateTo(testDomain.id);

    await expect(listPage.domainsTab).toBeVisible();
    await expect(listPage.keyRetrievalTab).toBeVisible();
    await listPage.openKeyRetrievalTab();
    await page.waitForURL(/\/settings\/trust-domains\/key-retrieval$/);

    const keyRetrievalPage = new TrustedDomainKeyRetrievalPage(page);
    await expect(keyRetrievalPage.allowUnsecuredHttpUriToggle).toBeVisible();
    await keyRetrievalPage.fetchTimeoutInput.fill('7500');
    await keyRetrievalPage.allowPrivateIpAddressToggle.click();
    await keyRetrievalPage.saveButton.click();
    await keyRetrievalPage.expectSnackbar(/updated/i);

    await keyRetrievalPage.navigateTo(testDomain.id);
    await expect(keyRetrievalPage.fetchTimeoutInput).toHaveValue('7500');
    await expect(keyRetrievalPage.allowPrivateIpAddressToggle).toBeChecked();
  });
});
