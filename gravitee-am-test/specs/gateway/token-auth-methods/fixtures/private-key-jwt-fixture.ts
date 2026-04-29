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
import { Fixture } from '../../../test-fixture';

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
