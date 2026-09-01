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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import { createTrustedIssuerKeyMaterial } from '../../../gateway/token-exchange/fixtures/trusted-issuer-jwt-helper';
import { patchDomainRaw } from '../../../gateway/token-exchange/fixtures/trusted-issuer-fixture';
import { TOKEN_EXCHANGE_TEST } from '../../../gateway/token-exchange/fixtures/token-exchange-fixture';

export interface CrossAppAccessResourceServerResponse {
  id?: string;
  name?: string;
  resource?: string;
}

export interface CrossAppAccessSettingsResponse {
  enabled?: boolean;
  resourceServers?: CrossAppAccessResourceServerResponse[];
  audSubMapping?: string;
  scopeMappings?: Record<string, string>;
}

export interface TrustedDomainResponse {
  id: string;
  name: string;
  domainIdentifier?: string;
  keyMaterial?: { source: string; certificate?: string };
  spiffe?: { spiffeTrustDomain?: string; allowedAlgorithms?: string[] };
  tokenExchange?: Record<string, unknown>;
  crossAppAccess?: CrossAppAccessSettingsResponse;
}

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
  /** An issuer identifier no other trusted domain of this suite holds. It is unique per security domain. */
  authorityIssuer: () => string;
  createTrustDomain: (body: Record<string, unknown>, targetDomainId?: string) => Promise<TrustedDomainResponse>;
  getTrustDomain: (trustDomainId: string, targetDomainId?: string) => Promise<TrustedDomainResponse>;
  updateTrustDomain: (trustDomainId: string, body: Record<string, unknown>, targetDomainId?: string) => Promise<TrustedDomainResponse>;
  /** Writes the deprecated inline trusted-issuer list, which replaces what the security domain trusts. */
  patchTrustedIssuers: (targetDomainId: string, trustedIssuers: Record<string, unknown>[]) => Promise<unknown>;
}

let issuerSequence = 0;

const trustedDomainsUrl = (domainId: string, trustDomainId?: string) =>
  `${process.env.AM_MANAGEMENT_URL}/management/organizations/${process.env.AM_DEF_ORG_ID}` +
  `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/trusted-domains${trustDomainId ? `/${trustDomainId}` : ''}`;

/**
 * The generated SDK still describes the flat, pre-split trusted-domain shape, so its serializer drops
 * the nested blocks. Raw fetch is the only way to post the v2 contract until it is regenerated.
 */
const callTrustedDomains = async (
  url: string,
  method: 'GET' | 'POST' | 'PUT',
  token: string,
  body?: object,
): Promise<TrustedDomainResponse> => {
  const response = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const payload = await response.text();
  if (!response.ok) {
    throw Object.assign(new Error(`${method} ${url} failed: ${response.status} ${payload}`), {
      response: { status: response.status, body: payload },
    });
  }
  return JSON.parse(payload);
};

/** Creates a trusted domain through the v2 contract, for a suite that does not need the whole fixture. */
export const createTrustedDomain = (domainId: string, accessToken: string, body: Record<string, unknown>): Promise<TrustedDomainResponse> =>
  callTrustedDomains(trustedDomainsUrl(domainId), 'POST', accessToken, body);

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
      resourceServers: [{ name: 'Calendar', resource }],
      audSubMapping: '{#user.email}',
      scopeMappings: { 'domain:read': 'calendar.read' },
      ...overrides,
    }),

    authorityIssuer: () => `https://auth.example.com/as-${++issuerSequence}`,

    createTrustDomain: (body: Record<string, unknown>, targetDomainId: string = domain.id) =>
      createTrustedDomain(targetDomainId, accessToken, body),

    getTrustDomain: (trustDomainId: string, targetDomainId: string = domain.id) =>
      callTrustedDomains(trustedDomainsUrl(targetDomainId, trustDomainId), 'GET', accessToken),

    updateTrustDomain: (trustDomainId: string, body: Record<string, unknown>, targetDomainId: string = domain.id) =>
      callTrustedDomains(trustedDomainsUrl(targetDomainId, trustDomainId), 'PUT', accessToken, body),

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
