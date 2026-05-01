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
import { generateSelfSignedCert } from '@utils/certificate-utils';
import { Agent } from 'https';
import fetch from 'cross-fetch';
import { Domain } from '@management-models/Domain';
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
import { Fixture } from '../../../test-fixture';

/** POST to a token endpoint through nginx mTLS, presenting a client certificate. */
export async function postWithClientCert(url: string, body: string, cert: string, key: string): Promise<Response> {
  const agent = new Agent({ rejectUnauthorized: false, cert, key });
  return fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
    agent,
  } as any) as Promise<Response>;
}

export interface MtlsAuthFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  tlsAuthClientId: string;
  selfSignedClientId: string;
  validCertPem: string;
  wrongCertPem: string;
  /** clientId of the tls_client_auth app configured for CN=a (used in nginx edge-stack tests) */
  edgeTlsClientId: string;
}

export const setupMtlsAuthFixture = async (): Promise<MtlsAuthFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    // Generate certs before domain setup; RSA 2048 generation takes ~1-2s each
    const validCert = generateSelfSignedCert('mtls-test-client');
    const wrongCert = generateSelfSignedCert('mtls-wrong-client');

    const { domain: createdDomain, oidcConfig } = await setupDomainForTest(uniqueName('mtls', true), {
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

    // tls_client_auth app — authenticated via certificate Subject DN
    const tlsApp = await createApplication(domain.id, accessToken, {
      name: uniqueName('mtls-tls-app', true),
      type: 'SERVICE',
      redirectUris: ['http://localhost:4000/'],
    });
    const updatedTlsApp = await waitForSyncAfter(domain.id, () =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: {
            oauth: {
              redirectUris: ['http://localhost:4000/'],
              grantTypes: ['client_credentials'],
              tokenEndpointAuthMethod: 'tls_client_auth',
              tlsClientAuthSubjectDn: validCert.subjectDn,
            },
          },
        },
        tlsApp.id,
      ),
    );

    // tls_client_auth app for edge-stack tests — Subject DN matches client-a cert (CN=a) from
    // gravitee-am-test/certs/client-a/, which is signed by the Test-CA trusted by gateway-mtls nginx.
    const edgeTlsApp = await createApplication(domain.id, accessToken, {
      name: uniqueName('mtls-edge-app', true),
      type: 'SERVICE',
      redirectUris: ['http://localhost:4000/'],
    });
    const updatedEdgeTlsApp = await waitForSyncAfter(domain.id, () =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: {
            oauth: {
              redirectUris: ['http://localhost:4000/'],
              grantTypes: ['client_credentials'],
              tokenEndpointAuthMethod: 'tls_client_auth',
              tlsClientAuthSubjectDn: 'CN=a',
            },
          },
        },
        edgeTlsApp.id,
      ),
    );

    // self_signed_tls_client_auth app — authenticated via certificate SHA-256 thumbprint.
    // Inline jwks is used because the jwksUri path goes through JWKConverter.convert(RSAKey)
    // which does not copy x5t#S256 to AM's JWK model, making getX5tS256() always null.
    // The inline path (getKeys(Client)) returns the stored JWKSet directly, preserving x5tS256.
    const ssApp = await createApplication(domain.id, accessToken, {
      name: uniqueName('mtls-ss-app', true),
      type: 'SERVICE',
      redirectUris: ['http://localhost:4000/'],
    });
    const updatedSsApp = await waitForSyncAfter(domain.id, () =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: {
            oauth: {
              redirectUris: ['http://localhost:4000/'],
              grantTypes: ['client_credentials'],
              tokenEndpointAuthMethod: 'self_signed_tls_client_auth',
              jwks: { keys: [{ kty: 'RSA', x5tS256: validCert.thumbprintS256 }] },
            },
          },
        },
        ssApp.id,
      ),
    );

    return {
      accessToken,
      domain,
      oidc: oidcConfig,
      tlsAuthClientId: updatedTlsApp.settings.oauth.clientId,
      selfSignedClientId: updatedSsApp.settings.oauth.clientId,
      validCertPem: validCert.pem,
      wrongCertPem: wrongCert.pem,
      edgeTlsClientId: updatedEdgeTlsApp.settings.oauth.clientId,
      cleanUp: async () => {
        await safeDeleteDomain(domain.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Cleanup failed after setup error:', e);
      }
    }
    throw error;
  }
};
