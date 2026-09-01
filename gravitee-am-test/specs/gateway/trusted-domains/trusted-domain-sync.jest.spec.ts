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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { listTrustDomains } from '@management-commands/trust-domain-management-commands';
import { getDomainState } from '@gateway-commands/monitoring-commands';
import { setup } from '../../test-fixture';
import {
  bothUsagesTrustDomainBody,
  issuerTrustDomainBody,
  setupTrustedDomainSyncFixture,
  spiffeTrustDomainBody,
  TRUSTED_DOMAIN_SYNC_TEST,
  TrustedDomainSyncFixture,
} from './fixtures/trusted-domain-sync-fixture';

setup(120000);

let fixture: TrustedDomainSyncFixture;

beforeAll(async () => {
  fixture = await setupTrustedDomainSyncFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('Trusted domain changes reach the gateway', () => {
  it('should synchronise the gateway when a SPIFFE trusted domain is registered', async () => {
    const body = spiffeTrustDomainBody();

    const created = await fixture.registerAndSync(body);

    expect(created.name).toEqual(body.name);
    expect(created.spiffeTrustDomain).toEqual(body.spiffeTrustDomain);
  });

  it('should synchronise the gateway when a trusted-issuer trusted domain is registered', async () => {
    const body = issuerTrustDomainBody();

    const created = await fixture.registerAndSync(body);

    expect(created.issuer).toEqual(body.issuer);
  });

  it('should synchronise the gateway when one trusted domain serves both usages', async () => {
    const body = bothUsagesTrustDomainBody();

    const created = await fixture.registerAndSync(body);

    expect(created.spiffeTrustDomain).toEqual(body.spiffeTrustDomain);
    expect(created.issuer).toEqual(body.issuer);
  });

  it('should synchronise the gateway when a trusted domain is amended', async () => {
    const registered = await fixture.registerAndSync(spiffeTrustDomainBody());

    const amended = await fixture.amendAndSync(registered.id, {
      keyMaterial: { source: 'JWKS_URL', jwksUrl: TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_AMENDED_JWKS_URL },
      refreshIntervalSeconds: TRUSTED_DOMAIN_SYNC_TEST.AMENDED_REFRESH_INTERVAL_SECONDS,
    });

    expect(amended.keyMaterial.jwksUrl).toEqual(TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_AMENDED_JWKS_URL);
    expect(amended.refreshIntervalSeconds).toEqual(TRUSTED_DOMAIN_SYNC_TEST.AMENDED_REFRESH_INTERVAL_SECONDS);
  });

  it('should synchronise the gateway when a trusted domain is removed', async () => {
    const kept = await fixture.registerAndSync(spiffeTrustDomainBody());
    const removed = await fixture.registerAndSync(issuerTrustDomainBody());

    await fixture.removeAndSync(removed.id);

    const remaining = (await listTrustDomains(fixture.domain.id, fixture.accessToken)).map((trustDomain) => trustDomain.id);
    expect(remaining).toContain(kept.id);
    expect(remaining).not.toContain(removed.id);
  });

  it('should report the domain as stable and synchronised once a change has been applied', async () => {
    await fixture.registerAndSync(spiffeTrustDomainBody());

    const state = await getDomainState(fixture.domain.id);

    expect(state.stable).toBe(true);
    expect(state.synchronized).toBe(true);
  });
});
