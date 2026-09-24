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

import { beforeAll, describe, expect, it } from '@jest/globals';
import { getDomain } from '@management-commands/domain-management-commands';
import { getDataPlaneTargets, getInstanceLabel } from '../../migration-seeding/seed';
import { TOKEN_EXCHANGE_VARIANTS, TRUSTED_ISSUER_SCOPE_MAPPINGS, TokenExchangeVariant } from '../../migration-seeding/token-exchange-seed';
import { createTokenExchangeMigrationFixture, TokenExchangeMigrationFixture } from './fixture/token-exchange-fixture';
import {
  channelHoldsTokenExchangeSeed,
  gatewaySupports,
  managementApiHasTrustedDomainsEndpoint,
  managementApiSupports,
  TOKEN_EXCHANGE_VERSION,
} from './fixture/migration-versions';
import { setup } from '../test-fixture';

setup(120000);

const channelLabel = process.env.AM_MIGRATION_TEST_LABEL || 'alpha';

// The trusted-issuer data set is only seeded from 4.12 on, so a channel seeded by an older tag has
// no domains to assert against.
const suite = channelHoldsTokenExchangeSeed(channelLabel) ? describe : describe.skip;

// Both consumer domains exist on every channel: `primary` carries the trusted issuer written with
// the API current for the seeded version, `legacy` the one written with the deprecated inline list.
// On the alpha channel `primary` is therefore the 4.12 shape the upgrade has to migrate; on the
// beta channel it is the trusted-domain entity, and `legacy` proves the old write path still works.
suite.each(getDataPlaneTargets())('migration token exchange [data plane $id]', (target) => {
  const label = getInstanceLabel(channelLabel, target.id);

  describe.each(TOKEN_EXCHANGE_VARIANTS)('trusted issuer seeded through the %s API', (variant: TokenExchangeVariant) => {
    let fixture: TokenExchangeMigrationFixture;

    beforeAll(async () => {
      fixture = await createTokenExchangeMigrationFixture(label, variant, target);
    });

    // A channel seeded by a recent version is still asserted after a downgrade; below 4.11 the
    // component under test has no token exchange at all, so there is nothing to assert against.
    const managementApiTest = managementApiSupports(TOKEN_EXCHANGE_VERSION) ? it : it.skip;
    const gatewayTest = gatewaySupports(TOKEN_EXCHANGE_VERSION) ? it : it.skip;

    // Asserted at every stage of the pipeline, whichever API wrote the issuer and whichever
    // versions are deployed: the inline list is the contract a 4.12 gateway reads and a 4.12
    // Management API serves, so it has to survive the upgrade — and the downgrade.
    managementApiTest('still reports the seeded trusted issuer on the security domain', async () => {
      const domain = await getDomain(fixture.consumerDomain.id, fixture.accessToken);
      const trustedIssuers = domain.tokenExchangeSettings?.trustedIssuers ?? [];
      const trustedIssuer = trustedIssuers.find((candidate) => candidate.issuer?.includes(fixture.issuerDomain.hrid));

      expect(domain.tokenExchangeSettings?.enabled).toBe(true);
      expect(trustedIssuer).toBeDefined();
      // The Management API serves the enum lower-cased, though the OpenAPI document spells it
      // JWKS_URL; compare on the name so the assertion survives either spelling.
      expect(String(trustedIssuer.keyResolutionMethod).toUpperCase()).toBe('JWKS_URL');
      expect(trustedIssuer.jwksUri).toContain(fixture.issuerDomain.hrid);
      expect(trustedIssuer.scopeMappings).toEqual(TRUSTED_ISSUER_SCOPE_MAPPINGS);
    });

    // The `trusted-domains` endpoint only exists from 4.13, so this is the one assertion that has
    // to wait for the Management API to be upgraded — the endpoint is new, not lost.
    const trustedDomainTest = managementApiHasTrustedDomainsEndpoint() ? it : it.skip;

    trustedDomainTest('also exposes the trusted issuer as a trusted domain of its own', async () => {
      const trustedDomains = await fixture.trustedDomains();
      const trustedDomain = trustedDomains.find((candidate) => candidate.domainIdentifier?.includes(fixture.issuerDomain.hrid));

      expect(trustedDomain).toBeDefined();
      // Lower-cased on the wire, like keyResolutionMethod above; compare on the name.
      expect(String(trustedDomain.keyMaterial?.source).toUpperCase()).toBe('JWKS_URL');
      expect(trustedDomain.keyMaterial?.jwksUrl).toContain(fixture.issuerDomain.hrid);
      expect(trustedDomain.tokenExchange?.enabled).toBe(true);
      expect(trustedDomain.tokenExchange?.scopeMappings).toEqual(TRUSTED_ISSUER_SCOPE_MAPPINGS);
    });

    gatewayTest('exchanges a JWT minted by the trusted security domain for a local access token', async () => {
      const issuerToken = await fixture.mintIssuerToken();

      const response = await fixture.exchange(issuerToken);

      expect(response.status).toBe(200);
      expect(response.body.access_token).toBeDefined();
      expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
      expect(response.body.token_type.toLowerCase()).toBe('bearer');
    });

    // Guards the test above against passing for the wrong reason: a gateway that no longer verifies
    // the issuer's signature at all would exchange anything presented to it.
    gatewayTest('refuses a JWT that the trusted issuer did not sign', async () => {
      const issuerToken = await fixture.mintIssuerToken();
      const tampered = `${issuerToken.slice(0, issuerToken.lastIndexOf('.'))}.ZGVhZGJlZWY`;

      const response = await fixture.exchange(tampered);

      expect(response.status).toBe(400);
      expect(response.body.error).toBe('invalid_request');
    });
  });
});
