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
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { expect } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { Fixture } from '../../../test-fixture';

export interface SelfAccountFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  user: {
    username: string;
    password: string;
  };
}

export const setupFixture = async (domainSettings: any): Promise<SelfAccountFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('self-account-change-password', true), 'Description');
  await patchDomain(domain.id, accessToken, domainSettings);
  const idpSet = await getAllIdps(domain.id, accessToken);

  const application = await createApplication(domain.id, accessToken, {
    name: 'name',
    type: 'WEB',
    clientId: uniqueName('selfaccount-changepwd', true),
    clientSecret: uniqueName('selfaccount-changepwd', true),
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code', 'password'],
            scopeSettings: [
              {
                scope: 'openid',
                defaultScope: true,
              },
              {
                scope: 'profile',
                defaultScope: true,
              },
              {
                scope: 'email',
                defaultScope: true,
              },
            ],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const username = uniqueName('SelfAccountUser', true);
  const user = {
    username: username,
    password: 'Password123!',
    firstName: 'SelfAccount',
    lastName: 'User',
    email: 'selfaccountuser@acme.fr',
    preRegistration: false,
  };
  await createUser(domain.id, accessToken, user);

  await startDomain(domain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(domain);
  return {
    domain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    application: application,
    user: user,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
