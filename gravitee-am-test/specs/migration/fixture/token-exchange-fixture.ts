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

import { expect } from '@jest/globals';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getDomainApi } from '../../../api/commands/management/service/utils';
import { Domain } from '../../../api/management/models/Domain';
import { DataPlaneTarget } from '../../../migration-seeding/seed';
import {
  ACCESS_TOKEN_TYPE,
  getTokenExchangeApplicationName,
  getTokenExchangeDomainName,
  getTokenExchangeIssuerApplicationName,
  getTokenExchangeIssuerDomainName,
  JWT_TOKEN_TYPE,
  listTrustedDomains,
  TOKEN_EXCHANGE_GRANT_TYPE,
  TokenExchangeVariant,
} from '../../../migration-seeding/token-exchange-seed';

export interface TokenExchangeMigrationFixture {
  accessToken: string;
  /** The security domain trusting the issuer, i.e. the one the exchange is performed against. */
  consumerDomain: Domain;
  /** The security domain whose only job is to mint the JWT to exchange. */
  issuerDomain: Domain;
  /** Mint an access token on the issuer domain, to be presented as a `…:token-type:jwt` subject. */
  mintIssuerToken: () => Promise<string>;
  /** Exchange a subject token on the consumer domain. Returns the raw response. */
  exchange: (subjectToken: string) => Promise<any>;
  /** The trusted domains the consumer domain exposes on the 4.13 `trusted-domains` endpoint. */
  trustedDomains: () => Promise<any[]>;
}

export async function createTokenExchangeMigrationFixture(
  label: string,
  variant: TokenExchangeVariant,
  target: DataPlaneTarget,
): Promise<TokenExchangeMigrationFixture> {
  const accessToken = await requestAdminAccessToken();
  const consumerDomain = await findSeededDomain(getTokenExchangeDomainName(label, variant), accessToken);
  const issuerDomain = await findSeededDomain(getTokenExchangeIssuerDomainName(label), accessToken);

  const issuerTokenEndpoint = (await openIdConfiguration(target.gatewayUrl, issuerDomain.hrid)).token_endpoint;
  const consumerTokenEndpoint = (await openIdConfiguration(target.gatewayUrl, consumerDomain.hrid)).token_endpoint;
  // The seed creates both applications with the client secret equal to the client id.
  const issuerAuth = basicAuthOf(getTokenExchangeIssuerApplicationName(label));
  const consumerAuth = basicAuthOf(getTokenExchangeApplicationName(label, variant));

  return {
    accessToken,
    consumerDomain,
    issuerDomain,
    mintIssuerToken: async () => {
      const response = await performPost(issuerTokenEndpoint, '', 'grant_type=client_credentials', {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${issuerAuth}`,
      }).expect(200);

      expect(response.body.access_token).toBeDefined();
      return response.body.access_token;
    },
    trustedDomains: () => listTrustedDomains(consumerDomain.id, accessToken),
    exchange: (subjectToken: string) =>
      performPost(
        consumerTokenEndpoint,
        '',
        `grant_type=${TOKEN_EXCHANGE_GRANT_TYPE}` +
          `&subject_token=${encodeURIComponent(subjectToken)}` +
          `&subject_token_type=${JWT_TOKEN_TYPE}` +
          `&requested_token_type=${ACCESS_TOKEN_TYPE}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${consumerAuth}`,
        },
      ),
  };
}

async function findSeededDomain(name: string, accessToken: string): Promise<Domain> {
  const domains = await getDomainApi(accessToken).listDomains({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    q: name,
  });
  const domain = domains.data?.find((candidate) => candidate.name === name);

  expect(domain).toBeDefined();
  return domain;
}

async function openIdConfiguration(gatewayUrl: string, domainHrid: string): Promise<Record<string, string>> {
  const response = await performGet(gatewayUrl, `/${domainHrid}/oidc/.well-known/openid-configuration`).expect(200);
  return response.body;
}

function basicAuthOf(applicationName: string): string {
  return getBase64BasicAuth(applicationName, applicationName);
}
