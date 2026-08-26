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
import { uniqueTestName } from '../../../utils/fixture-helpers';
import { alreadyExpired, validForDays } from '../../../utils/pkcs12-certificate-with-validity';

/**
 * AM-2220 / UC-AM69 — how the certificates page distinguishes certificates by expiry.
 *
 * A near-expiry certificate being flagged was already covered, but nothing showed a healthy one
 * going unflagged, so a Console that put a warning on every row would have passed. Both are
 * created in the same domain and read from the same page, so only the expiry date differs.
 *
 * The status is decided server-side in `CertificateEntity.determineStatus` against a configurable
 * threshold; a certificate lasting ten years and one lasting a day sit either side of it whatever
 * that threshold is set to.
 */
test.describe('Certificate expiry states (AM-2220)', () => {
  test('AM-2220: a near-expiry certificate is flagged and a healthy one is not', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2220');

    const healthyName = uniqueTestName('pw-healthy-cert');
    const nearExpiryName = uniqueTestName('pw-near-expiry-cert');

    await createCertificate(testDomain.id, adminToken, validForDays(healthyName, 3650));
    await createCertificate(testDomain.id, adminToken, validForDays(nearExpiryName, 1));
    await waitForDomainSync(testDomain.id);

    const certPage = new DomainCertificatesPage(page);
    await certPage.navigateTo(testDomain.id);

    const nearExpiryWarning = certPage.certificateRow(nearExpiryName).locator('mat-icon.warning');
    await expect(nearExpiryWarning).toBeVisible();
    // The tooltip carries the remaining life, so the warning says how urgent it is rather than
    // only that something is wrong.
    await expect(nearExpiryWarning).toHaveAttribute('aria-describedby', /.+/);

    // The half that was missing: without it, a page that flagged every row would still pass.
    await expect(certPage.certificateRow(healthyName).locator('mat-icon.warning')).toHaveCount(0);
  });

  test('AM-2220: a certificate that has already expired is refused on upload', async ({ testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2220');

    // The ticket asks what is shown for an expired certificate. Nothing is: one cannot be put
    // into a domain in the first place. Only a certificate that expires while installed reaches
    // that state, which a test cannot wait for.
    let status: number | undefined;
    let message = '';
    try {
      await createCertificate(testDomain.id, adminToken, alreadyExpired(uniqueTestName('pw-expired-cert')));
    } catch (e: any) {
      status = e?.response?.status;
      message = e?.message ?? '';
    }

    expect(status).toEqual(400);
    expect(message).toContain('already expired');
  });
});
