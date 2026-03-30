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
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface TokenAuthFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  app: Application;
  clientId: string;
  clientSecret: string;
  user: { username: string; password: string };
}

export interface TokenAuthSettings {
  tokenEndpointAuthMethod?: string;
  grantTypes?: string[];
}

export const setupTokenAuthFixture = async (settings: TokenAuthSettings = {}): Promise<TokenAuthFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    domain = await createDomain(accessToken, uniqueName('token-auth', true), 'Token auth methods test');

    await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
        },
      },
    });

    // Inline IdP with test user — auth method tests are about CLIENT auth, not IdP type
    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const idpUsers = [
      {
        firstname: 'Token',
        lastname: 'User',
        username: uniqueName('token-user', true),
        password: '#CoMpL3X-P@SsW0Rd',
      },
    ];
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({ users: idpUsers }),
      name: 'token-auth-idp',
    });

    const grantTypes = settings.grantTypes || ['client_credentials', 'password', 'refresh_token'];
    const app = await createApplication(domain.id, accessToken, {
      name: uniqueName('token-auth-app', true),
      type: 'WEB',
      redirectUris: ['http://localhost:4000/'],
    });
    const updatedApp = await updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['http://localhost:4000/'],
            grantTypes: grantTypes,
            tokenEndpointAuthMethod: settings.tokenEndpointAuthMethod || 'client_secret_basic',
          },
        },
        identityProviders: [{ identity: idp.id, priority: 0 }],
      },
      app.id,
    );
    updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;

    const startedDomain = await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(startedDomain);

    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      app: updatedApp,
      clientId: updatedApp.settings.oauth.clientId,
      clientSecret: updatedApp.settings.oauth.clientSecret,
      user: { username: idpUsers[0].username, password: idpUsers[0].password },
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try { await safeDeleteDomain(domain.id, accessToken); } catch (e) { console.error('Cleanup failed:', e); }
    }
    throw error;
  }
};
