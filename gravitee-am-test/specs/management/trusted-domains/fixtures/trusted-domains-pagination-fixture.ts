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
import { createTrustedDomain, TrustedDomainResponse } from '../../domain/fixtures/cross-app-access-fixture';

export interface TrustedDomainPage {
  data: TrustedDomainResponse[];
  page: number;
  totalCount: number;
  nextCursor?: string;
}

export type TrustedDomainsQuery = Record<string, string | number>;

export interface TrustedDomainsPaginationFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  listTrustedDomains: (query?: TrustedDomainsQuery) => Promise<TrustedDomainPage>;
  listTrustedDomainsStatus: (query: TrustedDomainsQuery) => Promise<number>;
  followNextCursor: (nextCursor: string) => Promise<TrustedDomainPage>;
}

const managementUrl = (path: string) => `${process.env.AM_MANAGEMENT_URL}/management${path}`;

const trustedDomainsPath = (domainId: string) =>
  `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/trusted-domains`;

const queryString = (query: TrustedDomainsQuery) =>
  new URLSearchParams(Object.entries(query).map(([name, value]) => [name, String(value)])).toString();

const get = (path: string, accessToken: string) => fetch(managementUrl(path), { headers: { Authorization: `Bearer ${accessToken}` } });

const readPage = async (response: Response): Promise<TrustedDomainPage> => {
  if (!response.ok) {
    throw new Error(`GET trusted domains failed: ${response.status} ${await response.text()}`);
  }
  return response.json();
};

const trustedDomainBodies = (certificate: string): Record<string, unknown>[] => {
  const keyMaterial = { source: 'PEM', certificate };
  return [
    {
      name: 'echo',
      domainIdentifier: 'https://echo.example.com',
      keyMaterial,
      crossAppAccess: crossAppAccess('https://echo.example.com/api'),
    },
    { name: 'charlie', spiffe: { spiffeTrustDomain: 'charlie.local' }, keyMaterial, crossAppAccess: { enabled: false } },
    { name: 'alpha', domainIdentifier: 'https://alpha.example.com', keyMaterial, tokenExchange: { enabled: true } },
    { name: 'Delta', spiffe: { spiffeTrustDomain: 'delta.local' }, keyMaterial },
    {
      name: 'bravo',
      spiffe: { spiffeTrustDomain: 'bravo.local' },
      domainIdentifier: 'https://bravo.example.com',
      keyMaterial,
      tokenExchange: { enabled: true },
    },
  ];
};

const crossAppAccess = (resource: string) => ({
  enabled: true,
  resourceServers: [{ name: 'Calendar', resource }],
});

export const setupTrustedDomainsPaginationFixture = async (): Promise<TrustedDomainsPaginationFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    domain = (await setupDomainForTest(uniqueName('td-pagination', true), { accessToken })).domain;

    const { certificatePem } = createTrustedIssuerKeyMaterial();
    for (const body of trustedDomainBodies(certificatePem)) {
      await createTrustedDomain(domain.id, accessToken, body);
    }

    const domainId = domain.id;
    const token = accessToken;

    return {
      accessToken,
      domain,
      listTrustedDomains: async (query = {}) => readPage(await get(`${trustedDomainsPath(domainId)}?${queryString(query)}`, token)),
      listTrustedDomainsStatus: async (query) => (await get(`${trustedDomainsPath(domainId)}?${queryString(query)}`, token)).status,
      followNextCursor: async (nextCursor) => readPage(await get(nextCursor, token)),
      cleanUp: async () => {
        await safeDeleteDomain(domainId, token);
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    throw error;
  }
};
