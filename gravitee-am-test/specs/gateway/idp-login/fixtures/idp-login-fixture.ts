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

// Follows pattern: login-flow-fixture.ts, oauth2-fixture.ts

export interface IdpLoginFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  app: Application;
  clientId: string;
  clientSecret: string;
  idp1: { id: string; users: Array<{ username: string; password: string }> };
  idp2?: { id: string; users: Array<{ username: string; password: string }> };
}

/**
 * Creates a domain with one inline IdP and an app.
 * Pass twoIdps=true to create a second IdP with a different user.
 */
export const setupIdpLoginFixture = async (opts: { twoIdps?: boolean } = {}): Promise<IdpLoginFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    domain = await createDomain(accessToken, uniqueName('idp-login', true), 'IdP login test');

    await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
        },
      },
    });

    // Delete default IdP
    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);

    // Create first inline IdP
    const idp1Users = [
      {
        firstname: 'User',
        lastname: 'One',
        username: uniqueName('user-one', true),
        password: '#CoMpL3X-P@SsW0Rd',
      },
    ];
    const idp1 = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({ users: idp1Users }),
      name: uniqueName('idp-one', true),
    });

    let idp2Result: IdpLoginFixture['idp2'] = undefined;
    const identityProviders: Array<{ identity: string; priority: number }> = [{ identity: idp1.id, priority: 0 }];

    if (opts.twoIdps) {
      const idp2Users = [
        {
          firstname: 'User',
          lastname: 'Two',
          username: uniqueName('user-two', true),
          password: '#CoMpL3X-P@SsW0Rd',
        },
      ];
      const idp2 = await createIdp(domain.id, accessToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        configuration: JSON.stringify({ users: idp2Users }),
        name: uniqueName('idp-two', true),
      });
      idp2Result = { id: idp2.id, users: idp2Users };
      identityProviders.push({ identity: idp2.id, priority: 1 });
    }

    // Create app
    const app = await createApplication(domain.id, accessToken, {
      name: uniqueName('idp-login-app', true),
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
            grantTypes: ['password', 'authorization_code'],
          },
        },
        identityProviders: identityProviders,
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
      idp1: { id: idp1.id, users: idp1Users },
      idp2: idp2Result,
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
