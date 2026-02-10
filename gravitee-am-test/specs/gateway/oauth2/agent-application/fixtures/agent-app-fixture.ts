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
import { createScope } from '@management-commands/scope-management-commands';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';
import { createCertificate } from '@management-commands/certificate-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../../test-fixture';
import { createDomainCertificate } from '../../fixture/oauth2-cert-fixture';

export interface AgentGatewayFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  user: { username: string; password: string };
  accessToken: string;
  cleanup: () => Promise<void>;
}

export const AGENT_GW_TEST = {
  DOMAIN_NAME_PREFIX: 'agent-gw',
  USER_PASSWORD: '#CoMpL3X-P@SsW0Rd',
  REDIRECT_URI: 'http://localhost:4000/',
} as const;

/**
 * Sets up a complete gateway test environment for AGENT application testing.
 * Creates all resources BEFORE starting the domain so the initial sync picks up everything.
 */
export const setupAgentGatewayFixture = async (): Promise<AgentGatewayFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // 1. Create domain and configure before starting
    domain = await createDomain(accessToken, uniqueName(AGENT_GW_TEST.DOMAIN_NAME_PREFIX, true), 'Agent gateway tests');
    domain = await patchDomain(domain.id, accessToken, {
      master: true,
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
          allowWildCardRedirectUri: true,
          isDynamicClientRegistrationEnabled: false,
          isOpenDynamicClientRegistrationEnabled: false,
        },
      },
    });
    expect(domain).toBeDefined();

    // 2. Create certificate
    await createDomainCertificate(domain, accessToken);

    // 3. Replace default IDP with inline IDP containing a test user
    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const idpConfig = {
      users: [
        {
          firstname: 'Agent',
          lastname: 'User',
          username: uniqueName('agent.user', true),
          password: AGENT_GW_TEST.USER_PASSWORD,
        },
      ],
    };
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify(idpConfig),
      name: 'inmemory',
    });
    expect(idp).toBeDefined();

    // 4. Create scope
    const scope = await createScope(domain.id, accessToken, {
      key: 'scope1',
      name: 'scope1',
      description: 'scope1',
    });
    expect(scope).toBeDefined();

    // 5. Create AGENT application
    const createdApp = await createApplication(domain.id, accessToken, {
      name: uniqueName('agent-client', true),
      type: 'AGENT',
      clientId: uniqueName('agent-cid', true),
      redirectUris: [AGENT_GW_TEST.REDIRECT_URI],
    });
    const application = await updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [AGENT_GW_TEST.REDIRECT_URI],
            grantTypes: ['client_credentials', 'authorization_code'],
            scopeSettings: [{ scope: 'scope1', defaultScope: true }],
          },
        },
        identityProviders: [{ identity: idp.id, priority: -1 }],
      },
      createdApp.id,
    );
    // Restore secret from create (update masks it)
    application.settings.oauth.clientSecret = createdApp.settings.oauth.clientSecret;
    expect(application).toBeDefined();

    // 6. Start domain — initial sync picks up all resources created above
    await startDomain(domain.id, accessToken);
    const domainWithOidc = await waitForDomainStart(domain);

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      oidc: domainWithOidc.oidcConfig,
      application,
      user: idpConfig.users[0],
      accessToken,
      cleanup,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
