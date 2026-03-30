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

// Follows pattern: remember-me-fixture.ts (manual create/patch/start for pre-start config)

export interface FlowPolicyFixture extends Fixture {
  domain: Domain;
  domainId: string;
  oidc: DomainOidcConfig;
  clientId: string;
  clientSecret: string;
  user: { username: string; password: string };
}

export const setupFlowPolicyFixture = async (): Promise<FlowPolicyFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    domain = await createDomain(accessToken, uniqueName('flow-policy', true), 'Flow policy regression test');

    await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
        },
      },
    });

    // Create inline IdP
    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const idpUsers = [{ firstname: 'Flow', lastname: 'User', username: uniqueName('flow-user', true), password: '#CoMpL3X-P@SsW0Rd' }];
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({ users: idpUsers }),
      name: uniqueName('flow-policy-idp', true),
    });

    // Create app with token claim that reads from authFlow context
    const app = await createApplication(domain.id, accessToken, {
      name: uniqueName('flow-app', true),
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
            grantTypes: ['password'],
            tokenCustomClaims: [
              {
                tokenType: 'ACCESS_TOKEN',
                claimName: 'groovy_claim',
                claimValue: "{#context.attributes['authFlow']['groovy-test']}",
              },
            ],
          },
        },
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
      domainId: domain.id,
      oidc: started.oidcConfig,
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
