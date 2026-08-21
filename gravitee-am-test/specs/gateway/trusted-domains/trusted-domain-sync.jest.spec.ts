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
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import {
  setupTrustedDomainSyncFixture,
  TRUSTED_DOMAIN_SYNC_TEST,
  TrustedDomainSyncFixture,
} from './fixtures/trusted-domain-sync-fixture';

setup(120000);
retryImmediatelyForThisFile();

let fixture: TrustedDomainSyncFixture;
let spiffeTrustDomainId: string;
let tokenExchangeTrustDomainId: string;

beforeAll(async () => {
  fixture = await setupTrustedDomainSyncFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

/**
 * Each step waits on the gateway's sync barrier. Before trusted-domain events were recognised
 * they resolved to no type and were dropped, so the barrier would never be crossed.
 */
describe('Trusted domain changes reach the gateway', () => {
  it('should synchronise the gateway when a SPIFFE trusted domain is registered', async () => {
    const created = await fixture.registerAndSync({
      name: TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_NAME,
      kind: 'SPIFFE',
      keyMaterial: { source: 'JWKS_URL', jwksUrl: TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_JWKS_URL },
    });

    spiffeTrustDomainId = created.id;
    expect(created.name).toEqual(TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_NAME);
  });

  it('should synchronise the gateway when a token-exchange trusted domain is registered', async () => {
    const created = await fixture.registerAndSync({
      name: TRUSTED_DOMAIN_SYNC_TEST.TOKEN_EXCHANGE_NAME,
      kind: 'TOKEN_EXCHANGE',
      keyMaterial: { source: 'JWKS_URL', jwksUrl: TRUSTED_DOMAIN_SYNC_TEST.TOKEN_EXCHANGE_JWKS_URL },
      tokenExchange: { issuer: TRUSTED_DOMAIN_SYNC_TEST.TOKEN_EXCHANGE_ISSUER },
    });

    tokenExchangeTrustDomainId = created.id;
    expect(created.tokenExchange.issuer).toEqual(TRUSTED_DOMAIN_SYNC_TEST.TOKEN_EXCHANGE_ISSUER);
  });

  it('should synchronise the gateway when a trusted domain is amended', async () => {
    const amended = await fixture.amendAndSync(spiffeTrustDomainId, {
      keyMaterial: { source: 'JWKS_URL', jwksUrl: TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_AMENDED_JWKS_URL },
      refreshIntervalSeconds: TRUSTED_DOMAIN_SYNC_TEST.AMENDED_REFRESH_INTERVAL_SECONDS,
    });

    expect(amended.keyMaterial.jwksUrl).toEqual(TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_AMENDED_JWKS_URL);
    expect(amended.refreshIntervalSeconds).toEqual(TRUSTED_DOMAIN_SYNC_TEST.AMENDED_REFRESH_INTERVAL_SECONDS);
  });

  it('should synchronise the gateway when a trusted domain is removed', async () => {
    await fixture.removeAndSync(tokenExchangeTrustDomainId);

    const remaining = await listTrustDomains(fixture.domain.id, fixture.accessToken);
    expect(remaining.map((trustDomain) => trustDomain.id)).toEqual([spiffeTrustDomainId]);
  });

  it('should report the domain as stable and synchronised once every change has been applied', async () => {
    const state = await getDomainState(fixture.domain.id);

    expect(state.stable).toBe(true);
    expect(state.synchronized).toBe(true);
  });
});
