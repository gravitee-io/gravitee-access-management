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
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import {
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  setupDomainForTest,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { privateJwk } from '@api-fixtures/oidc';
import * as jose from 'jose';
import crypto from 'crypto';
import { Fixture } from '../../../test-fixture';

const FORM = 'application/x-www-form-urlencoded';
const CLIENT_ASSERTION_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';

export interface PrivateKeyJwtFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  app: Application;
  clientId: string;
}

export const setupPrivateKeyJwtFixture = async (): Promise<PrivateKeyJwtFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    const { domain: createdDomain, oidcConfig } = await setupDomainForTest(uniqueName('pkjwt', true), {
      accessToken,
      waitForStart: true,
    });
    domain = createdDomain;

    await waitForSyncAfter(domain.id, () =>
      patchDomain(domain.id, accessToken, {
        oidc: {
          clientRegistrationSettings: {
            allowLocalhostRedirectUri: true,
            allowHttpSchemeRedirectUri: true,
          },
        },
      }),
    );
    await waitForOidcReady(domain.hrid);

    const app = await createApplication(domain.id, accessToken, {
      name: uniqueName('pkjwt-app', true),
      type: 'SERVICE',
      redirectUris: ['http://localhost:4000/'],
    });

    const updatedApp = await waitForSyncAfter(domain.id, () =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: {
            oauth: {
              redirectUris: ['http://localhost:4000/'],
              grantTypes: ['client_credentials'],
              tokenEndpointAuthMethod: 'private_key_jwt',
              tokenEndpointAuthSigningAlg: 'RS256',
              jwksUri: 'http://wiremock:8080/jwks/pkjwt-test-key',
            },
          },
        },
        app.id,
      ),
    );

    return {
      accessToken,
      domain,
      oidc: oidcConfig,
      app: updatedApp,
      clientId: updatedApp.settings.oauth.clientId,
      cleanUp: async () => {
        await safeDeleteDomain(domain.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Cleanup failed after setup error:', e);
      }
    }
    throw error;
  }
};

/** Mints an access token via private_key_jwt, signing a fresh client assertion for the fixture's client. */
export const mintPrivateKeyJwtToken = async (fixture: PrivateKeyJwtFixture): Promise<string> => {
  const assertion = await signClientAssertion(fixture);
  const response = await performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(
      CLIENT_ASSERTION_TYPE,
    )}&client_assertion=${encodeURIComponent(assertion)}`,
    { 'Content-type': FORM },
  ).expect(200);
  return response.body.access_token;
};

/** Signs a client assertion JWT for the fixture's client, using the registered key by default. */
export const signClientAssertion = async (
  fixture: PrivateKeyJwtFixture,
  key: jose.JWK = privateJwk as jose.JWK,
): Promise<string> => {
  const privateKey = await jose.importJWK(key, 'RS256');
  const now = Math.floor(Date.now() / 1000);
  return new jose.SignJWT({})
    .setProtectedHeader({ alg: 'RS256', kid: '123' })
    .setIssuer(fixture.clientId)
    .setSubject(fixture.clientId)
    .setAudience(fixture.oidc.token_endpoint)
    .setJti(crypto.randomUUID())
    .setIssuedAt(now)
    .setExpirationTime(now + 300)
    .sign(privateKey);
};
