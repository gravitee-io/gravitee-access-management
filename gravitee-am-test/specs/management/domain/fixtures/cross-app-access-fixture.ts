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

import type { Domain } from '@management-models/Domain';
import type { TrustDomain } from '@management-models/TrustDomain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createTrustDomain, getTrustDomain, updateTrustDomain } from '@management-commands/trust-domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import { createTrustedIssuerKeyMaterial } from '../../../gateway/token-exchange/fixtures/trusted-issuer-jwt-helper';
import { patchDomainRaw } from '../../../gateway/token-exchange/fixtures/trusted-issuer-fixture';
import { TOKEN_EXCHANGE_TEST } from '../../../gateway/token-exchange/fixtures/token-exchange-fixture';

export interface CrossAppAccessFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  /**
   * A second security domain, used only where the deprecated inline trusted-issuer list is written.
   * That list replaces what a security domain trusts, so it cannot share a domain with the tests
   * that assert their own trusted domains survive.
   */
  legacyDomain: Domain;
  /** Key material every trusted domain in this suite is created with, when it needs any. */
  pemKeyMaterial: { source: string; certificate: string };
  /** A complete, valid Cross App Access block, with the parts a test cares about overridden. */
  crossAppAccess: (resource: string, overrides?: Record<string, unknown>) => Record<string, unknown>;
  createTrustDomain: (body: Record<string, unknown>, targetDomainId?: string) => Promise<TrustDomain>;
  getTrustDomain: (trustDomainId: string, targetDomainId?: string) => Promise<TrustDomain>;
  updateTrustDomain: (trustDomainId: string, body: Record<string, unknown>, targetDomainId?: string) => Promise<TrustDomain>;
  /** Writes the deprecated inline trusted-issuer list, which replaces what the security domain trusts. */
  patchTrustedIssuers: (targetDomainId: string, trustedIssuers: Record<string, unknown>[]) => Promise<unknown>;
}

let audienceSequence = 0;

export const setupCrossAppAccessFixture = async (): Promise<CrossAppAccessFixture> => {
  const accessToken = await requestAdminAccessToken();
  const trustedKey = createTrustedIssuerKeyMaterial();

  const { domain } = await setupDomainForTest(uniqueName('xaa-valid', true), { accessToken, waitForStart: true });
  const { domain: legacyDomain } = await setupDomainForTest(uniqueName('xaa-legacy', true), { accessToken, waitForStart: true });

  return {
    accessToken,
    domain,
    legacyDomain,
    pemKeyMaterial: { source: 'PEM', certificate: trustedKey.certificatePem },

    crossAppAccess: (resource: string, overrides: Record<string, unknown> = {}) => ({
      enabled: true,
      audience: `https://auth.example.com/as-${++audienceSequence}`,
      resourceServers: [{ name: 'Calendar', resource }],
      audSubMapping: '{#user.email}',
      scopeMappings: { 'domain:read': 'calendar.read' },
      ...overrides,
    }),

    createTrustDomain: (body: Record<string, unknown>, targetDomainId: string = domain.id) =>
      createTrustDomain(targetDomainId, accessToken, body as any),

    getTrustDomain: (trustDomainId: string, targetDomainId: string = domain.id) =>
      getTrustDomain(targetDomainId, accessToken, trustDomainId),

    updateTrustDomain: (trustDomainId: string, body: Record<string, unknown>, targetDomainId: string = domain.id) =>
      updateTrustDomain(targetDomainId, accessToken, trustDomainId, body as any),

    patchTrustedIssuers: (targetDomainId: string, trustedIssuers: Record<string, unknown>[]) =>
      patchDomainRaw(targetDomainId, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
          trustedIssuers,
        },
      }).expect(200),

    cleanUp: async () => {
      await safeDeleteDomain(domain.id, accessToken);
      await safeDeleteDomain(legacyDomain.id, accessToken);
    },
  };
};
