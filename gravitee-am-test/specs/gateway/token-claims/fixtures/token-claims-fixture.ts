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

export interface TokenClaimsFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  app: Application;
  clientId: string;
  clientSecret: string;
  user: { username: string; password: string; email: string };
}

export interface TokenClaimsSettings {
  accessTokenValiditySeconds?: number;
  idTokenValiditySeconds?: number;
  tokenCustomClaims?: Array<{
    tokenType: 'ACCESS_TOKEN' | 'ID_TOKEN';
    claimName: string;
    claimValue: string;
  }>;
}

export const setupTokenClaimsFixture = async (settings: TokenClaimsSettings = {}): Promise<TokenClaimsFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    domain = await createDomain(accessToken, uniqueName('token-claims', true), 'Token claims and duration test');

    await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
        },
      },
    });

    // Create inline IdP with test user (including email for EL claim tests)
    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const idpUsers = [
      {
        firstname: 'Claims',
        lastname: 'User',
        username: uniqueName('claims-user', true),
        email: 'claims-user@test.gravitee.io',
        password: '#CoMpL3X-P@SsW0Rd',
      },
    ];
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({ users: idpUsers }),
      name: 'token-claims-idp',
    });

    // Create application with token settings
    const app = await createApplication(domain.id, accessToken, {
      name: uniqueName('token-claims-app', true),
      type: 'WEB',
      redirectUris: ['http://localhost:4000/'],
    });

    const oauthSettings: Record<string, unknown> = {
      redirectUris: ['http://localhost:4000/'],
      grantTypes: ['password', 'client_credentials'],
      scopeSettings: [{ scope: 'openid', defaultScope: false }],
    };

    if (settings.accessTokenValiditySeconds !== undefined) {
      oauthSettings.accessTokenValiditySeconds = settings.accessTokenValiditySeconds;
    }
    if (settings.idTokenValiditySeconds !== undefined) {
      oauthSettings.idTokenValiditySeconds = settings.idTokenValiditySeconds;
    }
    if (settings.tokenCustomClaims !== undefined) {
      oauthSettings.tokenCustomClaims = settings.tokenCustomClaims;
    }

    const updatedApp = await updateApplication(
      domain.id,
      accessToken,
      {
        settings: { oauth: oauthSettings },
        identityProviders: [{ identity: idp.id, priority: 0 }],
      },
      app.id,
    );
    // Preserve clientSecret from create (PUT response omits the secret)
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
      user: { username: idpUsers[0].username, password: idpUsers[0].password, email: idpUsers[0].email },
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
