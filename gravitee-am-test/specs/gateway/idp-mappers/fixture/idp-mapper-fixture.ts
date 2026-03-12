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
import { setupDomainForTest, safeDeleteDomain, DomainOidcConfig } from '@management-commands/domain-management-commands';
import { createIdp, updateIdp } from '@management-commands/idp-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { Fixture } from '../../../test-fixture';

export const JOHN = {
  username: 'john',
  password: '#CoMpL3X-P@SsW0Rd',
  firstname: 'John',
  lastname: 'Doe',
  email: 'john@example.com',
};

export const JANE = {
  username: 'jane',
  password: '#CoMpL3X-P@SsW0Rd',
  firstname: 'Jane',
  lastname: 'Smith',
  email: 'jane@example.com',
};

export const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';

export interface IdpMapperFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  openIdConfiguration: DomainOidcConfig;
  idp: IdentityProvider;
  app: Application;
  updateIdpMappers: (mappers: Record<string, string>, roleMapper: Record<string, string[]>, groupMapper: Record<string, string[]>) => Promise<IdentityProvider>;
}

export const setupIdpMapperFixture = async (): Promise<IdpMapperFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const { domain: d, oidcConfig } = await setupDomainForTest(uniqueName('idp-mapper', true), {
      accessToken,
      waitForStart: true,
    });
    domain = d;
    expect(domain.id).toBeDefined();

    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: [
          {
            firstname: JOHN.firstname,
            lastname: JOHN.lastname,
            username: JOHN.username,
            email: JOHN.email,
            password: JOHN.password,
          },
          {
            firstname: JANE.firstname,
            lastname: JANE.lastname,
            username: JANE.username,
            email: JANE.email,
            password: JANE.password,
          },
        ],
      }),
      name: uniqueName('inline-idp', true),
    });
    expect(idp.id).toBeDefined();

    const app = await waitForSyncAfter(domain.id, () =>
      createTestApp(uniqueName('mapper-app', true), domain, accessToken, 'WEB', {
        identityProviders: new Set([{ identity: idp.id, priority: -1 }]),
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid' }, { scope: 'profile' }, { scope: 'email' }],
          },
          advanced: { skipConsent: true },
        },
      }),
    );
    expect(app.id).toBeDefined();

    const updateIdpMappers = async (mappers: Record<string, string>, roleMapper: Record<string, string[]>, groupMapper: Record<string, string[]>): Promise<IdentityProvider> =>
      waitForSyncAfter(domain.id, () =>
        updateIdp(domain.id, accessToken, {
          name: idp.name,
          type: idp.type,
          configuration: idp.configuration,
          mappers,
          roleMapper,
          groupMapper,
        }, idp.id),
      );

    return {
      domain,
      accessToken,
      openIdConfiguration: oidcConfig,
      idp,
      app,
      updateIdpMappers,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after fixture setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
