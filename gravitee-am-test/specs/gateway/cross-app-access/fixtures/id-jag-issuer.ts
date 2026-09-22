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
import request from 'supertest';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { createTestApp } from '@utils-commands/application-commands';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { User } from '@management-models/User';
import { createTrustedIssuerKeyMaterial } from '../../token-exchange/fixtures/trusted-issuer-jwt-helper';
import { ID_TOKEN_TYPE, TOKEN_EXCHANGE_TEST } from '../../token-exchange/fixtures/token-exchange-fixture';
import { ID_JAG_TOKEN_TYPE } from '../../token-exchange/fixtures/id-jag-fixture';

export const enableIdJagTokenExchange = (domainId: string, accessToken: string) =>
  request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send({
      tokenExchangeSettings: {
        enabled: true,
        allowImpersonation: true,
        allowDelegation: false,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: [...TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES, ID_JAG_TOKEN_TYPE],
      },
    })
    .expect(200);

export const crossAppAccessTrustedDomain = () => {
  const trustedKey = createTrustedIssuerKeyMaterial();
  return (name: string, identifier: string, resources: Record<string, string>[], scopeMappings: Record<string, string>) => ({
    name: uniqueName(name),
    domainIdentifier: identifier,
    keyMaterial: { source: 'PEM', certificate: trustedKey.certificatePem },
    crossAppAccess: { enabled: true, resourceServers: resources, scopeMappings },
  });
};

export interface IdJagIssuerAppOptions {
  certificate: string;
  identityProvider: string;
  scopes: string[];
  resourceServers: Record<string, string>[];
  tokenCustomClaims?: Record<string, unknown>[];
}

export const createIdJagIssuerApp = (name: string, domain: Domain, accessToken: string, options: IdJagIssuerAppOptions) =>
  createTestApp(uniqueName(name, true), domain, accessToken, 'WEB', {
    certificate: options.certificate,
    settings: {
      oauth: {
        redirectUris: [TOKEN_EXCHANGE_TEST.REDIRECT_URI],
        grantTypes: ['password', 'urn:ietf:params:oauth:grant-type:token-exchange'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }, ...options.scopes.map((scope) => ({ scope, defaultScope: false }))],
        idJagValiditySeconds: 300,
        crossAppAccessSettings: { enabled: true, resourceServers: options.resourceServers },
        tokenCustomClaims: options.tokenCustomClaims ?? [],
      },
    },
    identityProviders: new Set([{ identity: options.identityProvider, priority: 0 }]),
  } as any);

export const requestIdToken = async (tokenEndpoint: string, application: Application, user: User, scope: string): Promise<string> => {
  const response = await performPost(
    tokenEndpoint,
    '',
    `grant_type=password&username=${user.username}&password=${TOKEN_EXCHANGE_TEST.USER_PASSWORD}&scope=${encodeURIComponent(scope)}`,
    { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${applicationBase64Token(application)}` },
  ).expect(200);
  return response.body.id_token;
};

export const exchangeForIdJag = async (
  tokenEndpoint: string,
  application: Application,
  idToken: string,
  audience: string,
  resource: string,
  scope: string,
): Promise<string> => {
  const body =
    `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
    `&subject_token=${idToken}` +
    `&subject_token_type=${ID_TOKEN_TYPE}` +
    `&requested_token_type=${ID_JAG_TOKEN_TYPE}` +
    `&audience=${encodeURIComponent(audience)}` +
    `&resource=${encodeURIComponent(resource)}` +
    `&scope=${encodeURIComponent(scope)}`;
  const response = await performPost(tokenEndpoint, '', body, {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: `Basic ${applicationBase64Token(application)}`,
  }).expect(200);
  return response.body.access_token;
};
