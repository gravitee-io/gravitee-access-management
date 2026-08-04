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
 * AM-7238 — Gateway enforcement of org license constraints.
 * Verifies that EE gateway plugins (SAML) are disabled when the org has no valid license,
 * and re-enabled when the license is restored. License enforcement applies on domain
 * update or gateway startup — not on license change alone.
 *
 * Requires: --cloud stack (local-stack.sh up --cloud).
 * The cloud overlay sets GRAVITEE_CLOUD_ENABLED=true + GRAVITEE_INSTALLATION_TYPE=managed
 * on both the management and gateway services.
 *
 * Tests are order-dependent; run with ci:cloud (--runInBand).
 */

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { waitForDomainStart } from '@management-commands/domain-management-commands';
import { getCertificateApi, getDomainApi, getIdpApi } from '@management-commands/service/utils';
import { getDomainState } from '@gateway-commands/monitoring-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { setup, retryImmediatelyForThisFile } from '../test-fixture';
import { jira } from '@specs-utils/jira';
import { OrgLicenseFixture, setupOrgLicenseFixture } from './fixtures/org-license-fixture';

setup(300000);
retryImmediatelyForThisFile();

// Computed at module load (not from the async fixture) so it's known before `it`/`it.skip` is chosen below.
const hasExpiredLicense = Boolean(process.env.AM_ORG_LICENSE_EXPIRED_PATH);
const DATA_PLANE_ID = process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';

const AZURE_AD_TYPE = 'azure-ad-am-idp';
const JKS_CONFIG = JSON.stringify({
  jks: '{"name":"server.jks","type":"","size":2237,"content":"/u3+7QAAAAIAAAABAAAAAQAJbXl0ZXN0a2V5AAABjNRK8OgAAAUBMIIE/TAOBgorBgEEASoCEQEBBQAEggTpvJkSxQizivQ8lHg0iLs3k1/PcaPrnyMPcmZR3k+E6Xo8BP6qdK8hq2yK1N11A7aMrwAcpxDFJ0VItku+wLYPBMZXAEEB1GFL0UMVtr+sP637ejLPGn8IwAzyAKwvHzOJzJ/I3jrKCdjgF60be3rN287xRVbtKmjFpWVHA707D3MklHEWTNsyKB5wofN8MDifqns1yvjjUn4fhrmETqDaIH7qkNPdjD/lnhppuw7oaRUti0Uma0GRd8WgifYMuXyNnWtLE15ZDIEpzcLWifAI3edmWLpMwdnT7HCTMKAqgT2mZwJk/JnfbICXrWGcO+t5kfnIejR+YUiijFZk/zWpl3q5TGHucTk4o+5pftZPYEzowW70qxCkxQUesh9sImAdXtBbfV4BvM0LP9D7EWZmHfxSCnVe7NS+hgATFyDLum5rFnUcp2S7BYa09U426EPXrQdmaN5RaJ55mhNL9S3DJ+KS/1+qvQRsoFThhsgbgSnFkv6O3kEu5KC6n8VL6u/51VkcRxiPXZHYAnRGUDQws4LCk4ZLg9oBP4tsZ7+6nw1pwTaXglcyT2H5bSb0Gr3HYpk4mwjbyQMINpI+YOLF/YZnuuZbZo3yWSC48b3cHfHQ71JcbiWh/glI8rJzdKc4b9hHQ8eAiuM7EhP/JuQs1+wIuZ19UERq7Bal3XMU/A112nONm3TY7dU/xfowuOry0YceMZLq4icb9Eo7fxzXkIvWmcaRx6S7KUVSs0pRbON8XqNGOd8TxSAiUjDZIuW86a8cf8JnRAEI8AAso4TdFn3hSDHg5icAWmIlvKViERqwG1xLc//JPT+1OOAguLkWi4KDh2ruYtDkkUEsw1mlnTdHMcrBsdTGkJnRf1KqqdeU7rt/jfmj2i6YevaOu6txU94ycJ5e2TJ0P1sFNwFaDOujLkKY1zTv3CIOo9myehBss+Y6Aa/6uUwaUJx1k9SNrcbqsphe4EX4I/oxeheygIS9CQFZ7PpTqKbmEnXcxAjjbAqoIHkUtpd7VN9lxmnxeemfF/1j9no2yN5x6dc4KJmA30SzoKOATLAWnXw4pXEu5UL9u3yOarjzSr/mN+NQumZ4jtQ+PdxNJdrXb7DLcvIibNRtfUlJWtNAEQQKnNnTJBiMF4Aw8ArF+gxFIf3sF0X0CZe7qWSRJgtgNt5QPSzjg32pnO1jDKYvAxekHZOOH7bGD9nWBpf8UuRNtvnsLCBbnTdWWB17RlO1vBDEe5p1KOPndmn3NtfGA02AHhLlTexx8FzPrt3XRXVHn+e9LS906qVu1i5lo3IEpt+rr03a8Vpeoyy8mLXukVJUxE1gt6c8Wb3ASr25NCTl06wqU1oIobjSk5wzwNpAF86IdSVCwEn6lAhkYsiDAg7gT97UC5nunfMiBZGjCbMlKnYawPRF7HGuZxN8wzPItKG+o76IFPgI6lWSfpxPa8RSBJUWV/BctY2IbovhvLg3LR/90OYLlAhgzLCZXZ20TTIPOQv5JpnsjN1n3ASV/NcNiJx3TrBPTwz5cDWCsz6mtipaDJLPFGQRWVcALkAeKqe8KXqTNafCB9Ry5oVQ2jQv2YvWLgnQCbkOlI7qe68Ur+ntSDtZBdcgcZ71X+CO3WzP8p+itVoZkJnAZGExIHSuYKNLyW43+ixiPF/dBRi8ZKt+n6FCUTHPbp7cAAAAAQAFWC41MDkAAAPCMIIDvjCCAqagAwIBAgIJANyU9PL6kmbCMA0GCSqGSIb3DQEBCwUAMIGDMRAwDgYDVQQGEwdDb3VudHJ5MQ0wCwYDVQQIEwRDaXR5MQ4wDAYDVQQHEwVTdGF0ZTETMBEGA1UEChMKTXkgQ29tcGFueTEcMBoGA1UECxMTTXkgQ29tcGFueSBEZXYgVGVhbTEdMBsGA1UEAxMUc2VydmVyLm15Y29tcGFueS5jb20wIBcNMjQwMTA0MTE0NTMwWhgPMjEyMzEyMTExMTQ1MzBaMIGDMRAwDgYDVQQGEwdDb3VudHJ5MQ0wCwYDVQQIEwRDaXR5MQ4wDAYDVQQHEwVTdGF0ZTETMBEGA1UEChMKTXkgQ29tcGFueTEcMBoGA1UECxMTTXkgQ29tcGFueSBEZXYgVGVhbTEdMBsGA1UEAxMUc2VydmVyLm15Y29tcGFueS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQChjv1u2Z56gjSMRDi7jiLE10ro8CCZbq5//J+1iO8urUH7vnRmmXwOqgoILRXsqq+sufS6qKEIa8HbQEWNb56qegrL/kh1gPxtTnNIh20ucWNawH46N5X2TK0hTNj9BaIYB8fbEgRAqALNI/fOS3KCOj7xIKWrbEfZVGuYtq+Wn3bdBijtsld2PYzi58i8qi+LpUPWyxZA4EQYYrLZLOVST+ttwKOmY4qmOEZ/NI6X5hIr98TkfbTlNHqT4scsRJAqq0JpBa7289piu+GfZ0PFFGQXKxu+ODIXRxR2kiLRlPPhpNX1FkAARokl1sM1CQcYbj66ilVWta4Uk3tFgxX9AgMBAAGjMTAvMB0GA1UdDgQWBBQH1PLdtVzXJkqJ46Ada7H4Ng3+bDAOBgNVHQ8BAf8EBAMCBaAwDQYJKoZIhvcNAQELBQADggEBAFr0LR3zoY1t+fT5H4SdblXiBQ+Tm7LPW4WeEU6WPenVCmgT0dXlT6ZQca2zquhW4ZMt3h2Kv/IrJ+ny0eUT7jEcIJ0NjzeuZOaOzQ7/HhJQCwEMBgWQ546jp0bQ212zez5VCe+UKfyrlpJmZwurGwBVbUfVkCwXVXRTnLwG+UFpLkwXJo3OvJ0bnHWvbj1Uy10WQNeP8L4xmkOFR9kVmPh0nX4STi8Ey5D6idXX+qhdx72reEDP5T5Qq5zjI+eE3xyHh8kE6AtiqSAKyJ8VAK6a+XiQMKvwRCxWEUnSPX8wQ7r2yKa77eXuiXb58+OHLgHm0GSubpvaa3ZKIOlxEmleegpqHo97N57SRm23gHT90cNsRg=="}',
  storepass: 'letmein',
  alias: 'mytestkey',
  keypass: 'changeme',
});

// License enforcement applies on domain update (~5s sync + ~230ms restart via sync-deployer).
const SYNC_OPTS = { timeoutMillis: 30000, intervalMillis: 1000 };

type PluginEntry = { id: string; type: string; success: boolean; message: string | null };

let fixture: OrgLicenseFixture;
let domainId: string;
let domainHrid: string;
let eeIdpId: string | null = null;

const orgEnv = () => ({ organizationId: fixture.organizationId, environmentId: fixture.environmentId });

async function getSamlStatus(hrid: string): Promise<number> {
  const res = await fetch(`${process.env.AM_GATEWAY_URL}/${hrid}/saml2/idp/metadata`);
  return res.status;
}

async function waitForSamlStatus(hrid: string, expected: number): Promise<void> {
  await retryUntil(
    () => getSamlStatus(hrid),
    (status) => status === expected,
    SYNC_OPTS,
  );
}

async function patchDomain(body: Record<string, unknown>): Promise<void> {
  await getDomainApi(fixture.accessToken).patchDomain({
    ...orgEnv(),
    domain: domainId,
    patchDomain: body,
  });
}

beforeAll(async () => {
  fixture = await setupOrgLicenseFixture('gateway-org-license');

  // Set universe license before domain creation so the gateway enforces it when the
  // domain first deploys. License enforcement applies on domain startup/update, not
  // on license change alone.
  await fixture.setUniverseLicense();

  const domainApi = getDomainApi(fixture.accessToken);
  const domain = await domainApi.createDomain({
    ...orgEnv(),
    newDomain: { name: uniqueName('am7238', true), description: 'AM-7238 gateway license', dataPlaneId: DATA_PLANE_ID },
  });
  domainId = domain.id!;
  domainHrid = domain.hrid!;

  const cert = await getCertificateApi(fixture.accessToken).createCertificate({
    ...orgEnv(),
    domain: domainId,
    newCertificate: {
      name: uniqueName('saml-cert'),
      type: 'javakeystore-am-certificate',
      configuration: JKS_CONFIG,
    },
  });

  await patchDomain({ saml: { enabled: true, entityId: domainHrid, certificate: cert.id } });
  await domainApi.patchDomain({ ...orgEnv(), domain: domainId, patchDomain: { enabled: true } });
  await waitForDomainStart(domain);
  await waitForSamlStatus(domainHrid, 200);
});

afterAll(async () => {
  if (eeIdpId && fixture) {
    await getIdpApi(fixture.accessToken)
      .deleteIdentityProvider({ ...orgEnv(), domain: domainId, identity: eeIdpId })
      .catch(() => null);
  }
  await fixture?.deleteDomain(domainId);
  await fixture?.cleanup();
});

describe('SAML metadata — universe org license', () => {
  it(jira`should return 200 with XML content when universe org license is active ${'AM-7238'}`, async () => {
    const res = await fetch(`${process.env.AM_GATEWAY_URL}/${domainHrid}/saml2/idp/metadata`);
    expect(res.status).toBe(200);
    const body = await res.text();
    expect(body).toContain('<?xml');
  });
});

describe('SAML metadata — org license removed', () => {
  beforeAll(async () => {
    const eeIdp = await getIdpApi(fixture.accessToken).createIdentityProvider({
      ...orgEnv(),
      domain: domainId,
      newIdentityProvider: {
        name: uniqueName('azure-for-readiness'),
        type: AZURE_AD_TYPE,
        configuration: JSON.stringify({ tenantId: 'test', clientId: 'test', clientSecret: 'test' }),
      },
    });
    eeIdpId = eeIdp.id!;
    // Clear the license then update the domain so the gateway re-evaluates the license
    // on the next sync-deployer redeploy.
    await fixture.clearOrgLicense();
    await patchDomain({ description: 'am7238-license-removed-trigger' });
    await waitForSamlStatus(domainHrid, 404);
  });

  it(jira`should return 404 after the org license is removed and the domain is updated ${'AM-7238'}`, async () => {
    const res = await fetch(`${process.env.AM_GATEWAY_URL}/${domainHrid}/saml2/idp/metadata`);
    expect(res.status).toBe(404);
  });

  it(jira`should show EE plugins (SAML) with a non-null license message in the domain readiness state ${'AM-7238'}`, async () => {
    const state = await getDomainState(domainId);
    const plugins = Object.values(state.creationState ?? {}) as PluginEntry[];
    const samlEntry = plugins.find((p) => p.id === 'saml2-idp');
    expect(samlEntry).toBeDefined();
    expect(samlEntry.success).toBe(true);
    expect(samlEntry.message).toEqual(expect.stringContaining('requires the feature'));
  });

  it(jira`should show OSS plugins with a null license message in the domain readiness state ${'AM-7238'}`, async () => {
    const state = await getDomainState(domainId);
    const plugins = Object.values(state.creationState ?? {}) as PluginEntry[];
    const ossEntry = plugins.find((p) => p.message === null && p.success === true);
    expect(ossEntry).toBeDefined();
  });

  it(jira`should keep the domain DEPLOYED, stable and synchronized without an org license ${'AM-7238'}`, async () => {
    const state = await getDomainState(domainId);
    expect(state.status).toBe('DEPLOYED');
    expect(state.stable).toBe(true);
    expect(state.synchronized).toBe(true);
  });
});

describe('Core OAuth — no org license', () => {
  it(jira`should return 302 on the authorize endpoint even when the org has no license ${'AM-7238'}`, async () => {
    const res = await fetch(
      `${process.env.AM_GATEWAY_URL}/${domainHrid}/oauth/authorize?response_type=code&client_id=test&redirect_uri=http://localhost`,
      { redirect: 'manual' },
    );
    expect(res.status).toBe(302);
  });
});

describe('SAML metadata — license restored', () => {
  beforeAll(async () => {
    // Restore the license then update the domain to trigger re-evaluation.
    await fixture.setUniverseLicense();
    await patchDomain({ description: 'am7238-license-restored-trigger' });
    await waitForSamlStatus(domainHrid, 200);
  });

  it(jira`should return 200 again after the universe license is restored and the domain is updated ${'AM-7238'}`, async () => {
    const res = await fetch(`${process.env.AM_GATEWAY_URL}/${domainHrid}/saml2/idp/metadata`);
    expect(res.status).toBe(200);
    const body = await res.text();
    expect(body).toContain('<?xml');
  });
});

describe('SAML metadata — expired org license', () => {
  // Skipped (visibly, in the test report) rather than early-returning, unless
  // AM_ORG_LICENSE_EXPIRED_PATH points at an expired license file to test against.
  (hasExpiredLicense ? it : it.skip)(
    jira`should return 404 when an expired org license is sent (treated same as no license) ${'AM-7238'}`,
    async () => {
      await fixture.setExpiredLicense();
      await patchDomain({ description: 'am7238-expired-license-trigger' });
      await waitForSamlStatus(domainHrid, 404);

      const res = await fetch(`${process.env.AM_GATEWAY_URL}/${domainHrid}/saml2/idp/metadata`);
      expect(res.status).toBe(404);
    },
  );
});
