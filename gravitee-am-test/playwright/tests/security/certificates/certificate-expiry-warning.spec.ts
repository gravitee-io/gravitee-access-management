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
import { createCertificate } from '@management-commands/certificate-management-commands';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { expect, test } from '../../../fixtures/base.fixture';
import { DomainCertificatesPage } from '../../../pages/domain-certificates.page';
import { linkJira } from '../../../utils/jira';
import { buildShortLivedPkcs12NewCertificate } from '../../../utils/short-lived-pkcs12-certificate';
import { uniqueTestName } from '../../../utils/fixture-helpers';

test.describe('Certificate expiry warning (AM-2220)', () => {
  test('AM-2220: near-expiry certificate shows warning icon in Console', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2220');

    const certDisplayName = uniqueTestName('pw-near-expiry');
    await createCertificate(testDomain.id, adminToken, buildShortLivedPkcs12NewCertificate(certDisplayName, 1));
    await waitForDomainSync(testDomain.id);

    const certPage = new DomainCertificatesPage(page);
    await certPage.navigateTo(testDomain.id);

    const row = certPage.certificateRow(certDisplayName);
    await expect(row.locator('mat-icon.warning')).toBeVisible();
  });
});
