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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { performPost, requestClientCredentialsToken } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { Fixture } from '../../../test-fixture';

/**
 * Two independent domains, each with:
 *  - a SERVICE application that mints access tokens via client_credentials
 *  - a confidential application acting as the resource server that calls /oauth/introspect
 *
 * Domain B exists purely to provide *valid* credentials and a *valid* endpoint that
 * belong to a different tenant, so cross-domain isolation can be exercised with
 * genuinely well-formed inputs rather than garbage.
 */
export interface IntrospectionClientAuthFixture extends Fixture {
  domainA: IntrospectionDomain;
  domainB: IntrospectionDomain;
  /** Mints a fresh client_credentials access token in the given domain. */
  issueToken: (domain: IntrospectionDomain) => Promise<string>;
  /** POSTs to the domain's introspection endpoint with an explicit Basic auth header. */
  introspectWithBasicAuth: (domain: IntrospectionDomain, token: string, credentials: { clientId: string; clientSecret: string }) => any;
  /** POSTs to the domain's introspection endpoint with no Authorization header at all. */
  introspectWithoutAuth: (domain: IntrospectionDomain, token: string) => any;
}

export interface IntrospectionDomain {
  domain: Domain;
  oidc: any;
  /** Application that mints the tokens under test. */
  tokenIssuer: Application;
  /** Confidential application that plays the resource server performing introspection. */
  resourceServer: Application;
}

export const INTROSPECTION_CLIENT_AUTH_TEST = {
  DOMAIN_A_PREFIX: 'introspect-auth-a',
  DOMAIN_B_PREFIX: 'introspect-auth-b',
  ISSUER_APP: 'introspect-issuer',
  RESOURCE_SERVER_APP: 'introspect-rs',
} as const;

const clientCredentialsSettings = {
  settings: {
    oauth: {
      grantTypes: ['client_credentials'],
    },
  },
};

async function setupDomain(prefix: string, accessToken: string): Promise<IntrospectionDomain> {
  const { domain, oidcConfig } = await setupDomainForTest(uniqueName(prefix, true), {
    accessToken,
    waitForStart: true,
  });
  expect(domain.id).toEqual(expect.any(String));
  expect(oidcConfig.introspection_endpoint).toEqual(expect.any(String));

  const tokenIssuer = await createTestApp(
    uniqueName(INTROSPECTION_CLIENT_AUTH_TEST.ISSUER_APP, true),
    domain,
    accessToken,
    'service',
    clientCredentialsSettings,
  );
  expect(tokenIssuer.settings.oauth.clientSecret).toEqual(expect.any(String));

  const resourceServer = await createTestApp(
    uniqueName(INTROSPECTION_CLIENT_AUTH_TEST.RESOURCE_SERVER_APP, true),
    domain,
    accessToken,
    'service',
    clientCredentialsSettings,
  );
  expect(resourceServer.settings.oauth.clientSecret).toEqual(expect.any(String));

  return { domain, oidc: oidcConfig, tokenIssuer, resourceServer };
}

export const setupIntrospectionClientAuthFixture = async (): Promise<IntrospectionClientAuthFixture> => {
  let accessToken: string | null = null;
  const createdDomainIds: string[] = [];

  try {
    accessToken = await requestAdminAccessToken();

    const domainA = await setupDomain(INTROSPECTION_CLIENT_AUTH_TEST.DOMAIN_A_PREFIX, accessToken);
    createdDomainIds.push(domainA.domain.id);

    const domainB = await setupDomain(INTROSPECTION_CLIENT_AUTH_TEST.DOMAIN_B_PREFIX, accessToken);
    createdDomainIds.push(domainB.domain.id);

    const issueToken = (target: IntrospectionDomain): Promise<string> =>
      requestClientCredentialsToken(
        target.tokenIssuer.settings.oauth.clientId,
        target.tokenIssuer.settings.oauth.clientSecret,
        target.oidc,
      );

    const introspectWithBasicAuth = (target: IntrospectionDomain, token: string, credentials: { clientId: string; clientSecret: string }) =>
      performPost(target.oidc.introspection_endpoint, '', `token=${encodeURIComponent(token)}`, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(credentials.clientId, credentials.clientSecret),
      });

    const introspectWithoutAuth = (target: IntrospectionDomain, token: string) =>
      performPost(target.oidc.introspection_endpoint, '', `token=${encodeURIComponent(token)}`, {
        'Content-type': 'application/x-www-form-urlencoded',
      });

    const cleanUp = async () => {
      for (const domainId of createdDomainIds) {
        await safeDeleteDomain(domainId, accessToken);
      }
    };

    return {
      accessToken,
      domainA,
      domainB,
      issueToken,
      introspectWithBasicAuth,
      introspectWithoutAuth,
      cleanUp,
    };
  } catch (error) {
    for (const domainId of createdDomainIds) {
      try {
        await safeDeleteDomain(domainId, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};

/** Credentials that match no client in any domain. */
export const UNKNOWN_CLIENT_CREDENTIALS = {
  clientId: 'no-such-client-id',
  clientSecret: 'no-such-client-secret',
} as const;
