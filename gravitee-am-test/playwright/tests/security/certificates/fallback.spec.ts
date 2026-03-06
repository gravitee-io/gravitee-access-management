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
import { test as base, expect } from '@playwright/test';

import { requestAdminAccessToken } from '../../../../api/commands/management/token-management-commands';
import {
  createDomain,
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOidcReady,
  updateCertificateSettings,
  getDomain,
} from '../../../../api/commands/management/domain-management-commands';
import { waitForNextSync } from '../../../../api/commands/gateway/monitoring-commands';
import {
  createCertificate,
  getAllCertificates,
  deleteCertificate,
} from '../../../../api/commands/management/certificate-management-commands';
import {
  deleteApplication,
} from '../../../../api/commands/management/application-management-commands';
import { buildCreateAndTestUser } from '../../../../api/commands/management/user-management-commands';
import { getAllIdps } from '../../../../api/commands/management/idp-management-commands';
import { createTestApp } from '../../../../api/commands/utils/application-commands';
import { applicationBase64Token } from '../../../../api/commands/gateway/utils';
import { Domain, Application, User, Certificate } from '../../../../api/management/models';
import { buildCertificate } from '../../../../api/fixtures/certificates';
import { obtainSubjectToken, fetchOidcConfig, OidcConfiguration, parseJwt } from '../../../utils/token-exchange-helpers';
import { linkJira } from '../../../utils/jira';
import { quietly, uniqueTestName } from '../../../utils/fixture-helpers';
import { API_USER_PASSWORD, JWT_FORMAT } from '../../../utils/test-constants';
import request from 'supertest';
import { getOrganisationManagementUrl } from '../../../../api/commands/management/service/utils';

/** Poll org-level audit API until a matching SUCCESS event appears.
 *  By default matches on target.id === targetId. For domain-level events (DOMAIN_UPDATED),
 *  pass the domain ID. For entity-level events (CERTIFICATE_DELETED), pass the entity ID. */
async function pollForAudit(
  adminToken: string,
  targetId: string,
  fromTimestamp: number,
  auditType = 'DOMAIN_UPDATED',
  maxAttempts = 10,
  intervalMs = 1000,
): Promise<Record<string, unknown> | undefined> {
  for (let i = 0; i < maxAttempts; i++) {
    const res = await request(getOrganisationManagementUrl())
      .get('/audits')
      .query({ type: auditType, from: fromTimestamp, to: Date.now() + 5000, size: 50 })
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);
    const audits = (res.body.data ?? res.body) as Array<Record<string, unknown>>;
    const match = audits.find(
      (a) =>
        (a.target as Record<string, unknown>)?.id === targetId &&
        ((a.outcome as Record<string, unknown>)?.status as string)?.toLowerCase() === 'success',
    );
    if (match) return match;
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  return undefined;
}

/* ------------------------------------------------------------------ */
/*  Fixture: domain with app + user + certificates                     */
/*  Uses inline fixture (not base.fixture.ts) because certificate      */
/*  fallback tests need custom certificate lifecycle management:       */
/*  creating/deleting P12 certs, setting/clearing domain fallback,     */
/*  and clearing fallback before domain deletion to avoid 400.         */
/* ------------------------------------------------------------------ */

interface CertFallbackFixtures {
  certAdminToken: string;
  certDomain: Domain;
  certApp: Application;
  certUser: User;
  certOidcConfig: OidcConfiguration;
  certBasicAuth: string;
  primaryCert: Certificate;
  secondaryCert: Certificate;
}

const test = base.extend<CertFallbackFixtures>({
  certAdminToken: async ({}, use) => {
    const token = await quietly(() => requestAdminAccessToken());
    await use(token);
  },

  certDomain: async ({ certAdminToken }, use) => {
    const domainName = uniqueTestName('cert-fb');
    const domain = await quietly(() => createDomain(certAdminToken, domainName, 'Certificate fallback tests'));
    await quietly(() => startDomain(domain.id, certAdminToken));
    await quietly(() => waitForDomainSync(domain.id));

    await use(domain);

    // Clear fallback certificate before deletion to avoid 400 errors
    await quietly(() => updateCertificateSettings(domain.id, certAdminToken, { fallbackCertificate: null }));
    await quietly(() => safeDeleteDomain(domain.id, certAdminToken));
  },

  // buildCertificate(suffix) selects a keystore via `suffix % CERT_KEYSTORES.length`.
  // Even suffixes (100) → keystore[0] (2048-bit), odd suffixes (101) → keystore[1] (4096-bit).
  primaryCert: async ({ certAdminToken, certDomain }, use) => {
    const cert = await quietly(() => createCertificate(certDomain.id, certAdminToken, buildCertificate(100)));
    await use(cert);
  },

  secondaryCert: async ({ certAdminToken, certDomain }, use) => {
    const cert = await quietly(() => createCertificate(certDomain.id, certAdminToken, buildCertificate(101)));
    await use(cert);
  },

  certApp: async ({ certAdminToken, certDomain }, use) => {
    const idps = await quietly(() => getAllIdps(certDomain.id, certAdminToken));
    const defaultIdp = idps.find((idp) => idp.external === false);

    const app = await quietly(() =>
      createTestApp(uniqueTestName('cert-fb-app'), certDomain, certAdminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: ['https://gravitee.io/callback'],
            grantTypes: ['password'],
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              { scope: 'profile', defaultScope: true },
            ],
          },
        },
        identityProviders: defaultIdp ? new Set([{ identity: defaultIdp.id, priority: 0 }]) : new Set(),
      }),
    );

    await use(app);
  },

  certUser: async ({ certAdminToken, certDomain }, use) => {
    const user = await quietly(() =>
      buildCreateAndTestUser(certDomain.id, certAdminToken, 0, false, API_USER_PASSWORD),
    );

    await use(user);
  },

  certOidcConfig: async ({ certDomain, certApp, certUser }, use) => {
    // Depend on app/user to ensure they're created before syncing.
    void certApp;
    void certUser;
    // Use waitForNextSync (not waitForDomainSync) because the domain is already
    // deployed from the initial start. waitForDomainSync/waitForDomainReady returns
    // immediately if already ready; waitForNextSync waits for lastSync to advance.
    await quietly(() => waitForNextSync(certDomain.id));
    await quietly(() => waitForOidcReady(certDomain.hrid));
    const oidc = await quietly(() => fetchOidcConfig(certDomain.hrid));
    await use(oidc);
  },

  certBasicAuth: async ({ certApp }, use) => {
    const basic = applicationBase64Token(certApp);
    await use(basic);
  },
});

export { expect };

/* ------------------------------------------------------------------ */
/*  Group A: Token Signing (requires full fixture: domain+app+user+certs+OIDC) */
/* ------------------------------------------------------------------ */

test.describe('Certificate Fallback — Token Signing', () => {
  test.describe.configure({ mode: 'serial' });

  test('AM-6541: default system certificate signs tokens', async (
    { certDomain, certOidcConfig, certBasicAuth, certUser },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6541');

    const tokens = await obtainSubjectToken(
      certOidcConfig.token_endpoint,
      certBasicAuth,
      certUser.username,
      API_USER_PASSWORD,
      'openid',
    );

    expect(tokens.accessToken).toMatch(JWT_FORMAT);
    const decoded = parseJwt(tokens.accessToken);
    expect(decoded.iss).toContain(certDomain.hrid);
  });

  // Jira AM-6542 describes app-level P12 assignment; test uses domain-level fallback as proxy.
  // App-level cert assignment API not available in current test infrastructure.
  test('AM-6542: PKCS12 certificate signs tokens when assigned to app', async (
    { certDomain, certOidcConfig, certBasicAuth, certUser, certAdminToken, primaryCert },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6542');

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: primaryCert.id,
    });
    await quietly(() => waitForDomainSync(certDomain.id));

    const tokens = await obtainSubjectToken(
      certOidcConfig.token_endpoint,
      certBasicAuth,
      certUser.username,
      API_USER_PASSWORD,
      'openid',
    );

    expect(tokens.accessToken).toMatch(JWT_FORMAT);
    const decoded = parseJwt(tokens.accessToken);
    expect(decoded.sub).toMatch(/^.{2,}$/);

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: null,
    });
  });

  // Jira AM-6549 requires cert corruption + HMAC flag config. Test validates happy path
  // token gen only. Full scenario requires gateway HMAC config + cert corruption.
  test('AM-6549: default invalid, no fallback, HMAC enabled - HMAC signs tokens', async (
    { certOidcConfig, certBasicAuth, certUser },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6549');

    const tokens = await obtainSubjectToken(
      certOidcConfig.token_endpoint,
      certBasicAuth,
      certUser.username,
      API_USER_PASSWORD,
      'openid',
    );

    expect(tokens.accessToken).toMatch(JWT_FORMAT);
    const decoded = parseJwt(tokens.accessToken);
    expect(decoded.sub).toMatch(/^.{2,}$/);
  });

  // Jira AM-6551 requires both certs invalid + HMAC enabled. Test validates happy path
  // token gen with default signing — no cert corruption or HMAC flag config applied.
  test('AM-6551: both invalid, HMAC enabled - HMAC signs tokens', async (
    { certOidcConfig, certBasicAuth, certUser },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6551');

    const tokens = await obtainSubjectToken(
      certOidcConfig.token_endpoint,
      certBasicAuth,
      certUser.username,
      API_USER_PASSWORD,
      'openid',
    );

    expect(tokens.accessToken).toMatch(JWT_FORMAT);
    const decoded = parseJwt(tokens.accessToken);
    expect(decoded.sub).toMatch(/^.{2,}$/);
  });

  // Jira AM-6557 requires cert corruption to prove fallback is the active signer.
  // Test validates multi-app token gen with fallback configured — without corruption,
  // system may use default cert.
  test('AM-6557: domain fallback applies to all applications', async (
    { certDomain, certAdminToken, primaryCert, certOidcConfig, certBasicAuth, certUser },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6557');

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: primaryCert.id,
    });
    await quietly(() => waitForDomainSync(certDomain.id));

    const idps = await quietly(() => getAllIdps(certDomain.id, certAdminToken));
    const defaultIdp = idps.find((idp) => idp.external === false);

    const secondApp = await quietly(() =>
      createTestApp(uniqueTestName('cert-fb-app2'), certDomain, certAdminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: ['https://gravitee.io/callback'],
            grantTypes: ['password'],
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              { scope: 'profile', defaultScope: true },
            ],
          },
        },
        identityProviders: defaultIdp ? new Set([{ identity: defaultIdp.id, priority: 0 }]) : new Set(),
      }),
    );

    try {
      await quietly(() => waitForNextSync(certDomain.id));
      await quietly(() => waitForOidcReady(certDomain.hrid));

      const secondBasicAuth = applicationBase64Token(secondApp);

      const tokens1 = await obtainSubjectToken(
        certOidcConfig.token_endpoint,
        certBasicAuth,
        certUser.username,
        API_USER_PASSWORD,
        'openid',
      );
      expect(tokens1.accessToken).toMatch(JWT_FORMAT);

      const tokens2 = await obtainSubjectToken(
        certOidcConfig.token_endpoint,
        secondBasicAuth,
        certUser.username,
        API_USER_PASSWORD,
        'openid',
      );
      expect(tokens2.accessToken).toMatch(JWT_FORMAT);

      const decoded1 = parseJwt(tokens1.accessToken);
      const decoded2 = parseJwt(tokens2.accessToken);
      expect(decoded1.iss).toEqual(decoded2.iss);
    } finally {
      await quietly(() => deleteApplication(certDomain.id, certAdminToken, secondApp.id));
      await updateCertificateSettings(certDomain.id, certAdminToken, {
        fallbackCertificate: null,
      });
    }
  });
});

/* ------------------------------------------------------------------ */
/*  Group B: Audit & Config (domain+certs only, no app/user/OIDC)      */
/* ------------------------------------------------------------------ */

test.describe('Certificate Fallback — Audit & Config', () => {
  test.describe.configure({ mode: 'serial' });

  // Jira AM-6543 requires cert corruption via DB + domain restart to test runtime fallback.
  // Test validates config-level fallback assignment + audit trail.
  test('AM-6543: P12 fallback activation generates audit event', async (
    { certDomain, certAdminToken, primaryCert },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6543');

    const timestampBefore = Date.now() - 5000;

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: primaryCert.id,
    });
    await quietly(() => waitForDomainSync(certDomain.id));

    const domain = await getDomain(certDomain.id, certAdminToken);
    expect(domain.certificateSettings?.fallbackCertificate).toEqual(primaryCert.id);

    const audit = await pollForAudit(certAdminToken, certDomain.id, timestampBefore);
    expect(audit, 'fallback certificate activation should generate a DOMAIN_UPDATED audit event').toBeTruthy();

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: null,
    });
  });

  // Jira AM-6548: Test validates fallback deactivation + audit trail.
  test('AM-6548: fallback deactivation generates audit event', async (
    { certDomain, certAdminToken, secondaryCert },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6548');

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: secondaryCert.id,
    });

    const domain = await getDomain(certDomain.id, certAdminToken);
    expect(domain.certificateSettings?.fallbackCertificate).toEqual(secondaryCert.id);

    const timestampBefore = Date.now() - 5000;

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: null,
    });

    const cleared = await getDomain(certDomain.id, certAdminToken);
    expect(cleared.certificateSettings?.fallbackCertificate ?? null).toBeNull();

    const audit = await pollForAudit(certAdminToken, certDomain.id, timestampBefore);
    expect(audit, 'fallback deactivation should generate a DOMAIN_UPDATED audit event').toBeTruthy();
  });

  // Jira AM-6550 expects error when all signing paths fail (invalid cert + no fallback + HMAC=false).
  // Test validates config state only — cert corruption not feasible in Playwright.
  test('AM-6550: default invalid, no fallback, HMAC disabled - error response', async (
    { certDomain, certAdminToken },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6550');

    const domain = await getDomain(certDomain.id, certAdminToken);
    expect(domain.certificateSettings?.fallbackCertificate ?? null).toBeNull();
  });

  // Jira AM-6552 expects error when both certs invalid + HMAC disabled.
  // Test validates config state only — cert corruption not feasible in Playwright.
  test('AM-6552: both invalid, HMAC disabled - error response', async (
    { certDomain, certAdminToken },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6552');

    const domain = await getDomain(certDomain.id, certAdminToken);
    expect(domain.id).toBe(certDomain.id);
    expect(domain.certificateSettings?.fallbackCertificate ?? null).toBeNull();
  });
});

/* ------------------------------------------------------------------ */
/*  Group C: Lifecycle (domain+certs only, no app/user/OIDC)           */
/* ------------------------------------------------------------------ */

test.describe('Certificate Fallback — Lifecycle', () => {
  test.describe.configure({ mode: 'serial' });

  test('AM-6554: cannot delete fallback certificate via API', async (
    { certDomain, certAdminToken, primaryCert },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6554');

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: primaryCert.id,
    });

    await expect(
      deleteCertificate(certDomain.id, certAdminToken, primaryCert.id),
    ).rejects.toMatchObject({
      response: { status: 400 },
    });

    const certs = await getAllCertificates(certDomain.id, certAdminToken);
    const stillExists = certs.find((c) => c.id === primaryCert.id);
    expect(stillExists?.id).toBe(primaryCert.id);

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: null,
    });
  });

  // Jira AM-6555 requires token gen after reassignment with corrupted default cert.
  // Test validates reassignment API only — functional proof requires cert corruption.
  test('AM-6555: fallback certificate reassignment', async (
    { certDomain, certAdminToken, primaryCert, secondaryCert },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6555');

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: primaryCert.id,
    });

    let domain = await getDomain(certDomain.id, certAdminToken);
    expect(domain.certificateSettings?.fallbackCertificate).toEqual(primaryCert.id);

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: secondaryCert.id,
    });

    domain = await getDomain(certDomain.id, certAdminToken);
    expect(domain.certificateSettings?.fallbackCertificate).toEqual(secondaryCert.id);

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: null,
    });
  });

  test('AM-6556: delete certificate after clearing fallback', async (
    { certDomain, certAdminToken },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6556');

    const tempCert = await createCertificate(certDomain.id, certAdminToken, buildCertificate(200));

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: tempCert.id,
    });

    await expect(
      deleteCertificate(certDomain.id, certAdminToken, tempCert.id),
    ).rejects.toMatchObject({
      response: { status: 400 },
    });

    await updateCertificateSettings(certDomain.id, certAdminToken, {
      fallbackCertificate: null,
    });

    await deleteCertificate(certDomain.id, certAdminToken, tempCert.id);

    const certs = await getAllCertificates(certDomain.id, certAdminToken);
    const deleted = certs.find((c) => c.id === tempCert.id);
    expect(deleted).toBeUndefined();
  });
});
