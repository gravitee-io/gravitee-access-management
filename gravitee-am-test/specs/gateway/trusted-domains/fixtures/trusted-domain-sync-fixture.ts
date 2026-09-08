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

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { patchDomain, safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { Domain } from '@management-models/Domain';
import { uniqueName } from '@utils-commands/misc';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { Fixture } from '../../../test-fixture';

export const TRUSTED_DOMAIN_SYNC_TEST = {
  DOMAIN_NAME_PREFIX: 'trusted-domain-sync',
  SPIFFE_JWKS_URL: 'https://spire.example.com/keys',
  SPIFFE_AMENDED_JWKS_URL: 'https://spire.example.com/rotated-keys',
  TOKEN_EXCHANGE_JWKS_URL: 'https://issuer.example.com/.well-known/jwks.json',
  BOTH_JWKS_URL: 'https://sso.acme.com/.well-known/jwks.json',
  AMENDED_REFRESH_INTERVAL_SECONDS: 600,
} as const;

const distinctLabel = (prefix: string) => uniqueName(prefix, true).toLowerCase();

const trustDomainsUrl = (domainId: string, trustDomainId?: string) =>
  `${process.env.AM_MANAGEMENT_URL}/management/organizations/${process.env.AM_DEF_ORG_ID}` +
  `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/trusted-domains${trustDomainId ? `/${trustDomainId}` : ''}`;

export interface TrustedDomainResponse {
  id: string;
  name: string;
  domainIdentifier?: string;
  keyMaterial?: { source: string; jwksUrl?: string; refreshIntervalSeconds?: number };
  spiffe?: { spiffeTrustDomain?: string; allowedAlgorithms?: string[] };
  tokenExchange?: Record<string, unknown>;
}

const writeTrustDomain = async (url: string, method: 'POST' | 'PUT', token: string, body: object): Promise<TrustedDomainResponse> => {
  const response = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${method} ${url} failed: ${response.status} ${await response.text()}`);
  }
  return response.json();
};

/** Body of a trusted domain matching SPIFFE only, named so it collides with no other test's. */
export const spiffeTrustDomainBody = () => {
  const label = distinctLabel('spiffe-sync');
  return {
    name: label,
    spiffe: { spiffeTrustDomain: `${label}.local` },
    keyMaterial: { source: 'JWKS_URL', jwksUrl: TRUSTED_DOMAIN_SYNC_TEST.SPIFFE_JWKS_URL },
  };
};

/** Body of a trusted domain vouching for an issuer only, named so it collides with no other test's. */
export const issuerTrustDomainBody = () => {
  const label = distinctLabel('issuer-sync');
  return {
    name: label,
    domainIdentifier: `https://${label}.example.com`,
    tokenExchange: {},
    keyMaterial: { source: 'JWKS_URL', jwksUrl: TRUSTED_DOMAIN_SYNC_TEST.TOKEN_EXCHANGE_JWKS_URL },
  };
};

/** Body of a trusted domain serving both usages over the same key material. */
export const bothUsagesTrustDomainBody = () => {
  const label = distinctLabel('both-sync');
  return {
    name: label,
    spiffe: { spiffeTrustDomain: `${label}.local` },
    domainIdentifier: `https://${label}.example.com`,
    tokenExchange: {},
    keyMaterial: { source: 'JWKS_URL', jwksUrl: TRUSTED_DOMAIN_SYNC_TEST.BOTH_JWKS_URL },
  };
};

export interface TrustedDomainSyncFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  /** Registers a trusted domain and waits for the gateway to apply the change. */
  registerAndSync: (body: object) => Promise<TrustedDomainResponse>;
  /** Amends a trusted domain and waits for the gateway to apply the change. */
  amendAndSync: (trustDomainId: string, body: object) => Promise<TrustedDomainResponse>;
  /** Removes a trusted domain and waits for the gateway to apply the change. */
  removeAndSync: (trustDomainId: string) => Promise<void>;
}

export const setupTrustedDomainSyncFixture = async (): Promise<TrustedDomainSyncFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    const domainResult = await setupDomainForTest(uniqueName(TRUSTED_DOMAIN_SYNC_TEST.DOMAIN_NAME_PREFIX, true), {
      accessToken,
      waitForStart: true,
    });
    domain = domainResult.domain;

    await patchDomain(domain.id, accessToken, {
      oidc: {
        workloadIdentitySettings: { enabled: true },
      },
      keyRetrievalSettings: { allowPrivateIpAddress: true, allowUnsecuredHttpUri: true },
    });

    const token = accessToken;
    const domainId = domain.id;

    return {
      domain,
      accessToken,
      registerAndSync: (body) => waitForSyncAfter(domainId, () => writeTrustDomain(trustDomainsUrl(domainId), 'POST', token, body)),
      amendAndSync: (trustDomainId, body) =>
        waitForSyncAfter(domainId, () => writeTrustDomain(trustDomainsUrl(domainId, trustDomainId), 'PUT', token, body)),
      removeAndSync: (trustDomainId) =>
        waitForSyncAfter(domainId, async () => {
          const response = await fetch(trustDomainsUrl(domainId, trustDomainId), {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${token}` },
          });
          if (!response.ok) {
            throw new Error(`DELETE trusted domain failed: ${response.status} ${await response.text()}`);
          }
        }),
      cleanUp: async () => {
        await safeDeleteDomain(domainId, token);
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
