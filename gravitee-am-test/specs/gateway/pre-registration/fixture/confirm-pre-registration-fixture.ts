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
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { Application } from '@management-models/Application';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';

export interface ConfirmPreRegistrationFixture {
  domain: Domain;
  accessToken: string;
  oidc: DomainOidcConfig;
  application: Application;
  clientId: string;
  cleanup: () => Promise<void>;
}

export const setupFixture = async (): Promise<ConfirmPreRegistrationFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('pre-registration', true), 'Description');
  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;

  const application = await createApplication(domain.id, accessToken, {
    name: 'appName',
    type: 'WEB',
    clientId: 'appClientId',
    clientSecret: 'appClientSecret',
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
          },
          login: {
            inherited: false,
            forgotPasswordEnabled: true,
          },
        },
        identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  await startDomain(domain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(domain);

  return {
    domain: domain,
    oidc: domainWithOidc.oidcConfig,
    accessToken: accessToken,
    application: application,
    clientId: application.settings.oauth.clientId,
    cleanup: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
