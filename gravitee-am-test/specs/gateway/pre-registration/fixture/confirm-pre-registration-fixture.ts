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
import { DomainOidcConfig, safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { Application } from '@management-models/Application';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';

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
  const { domain, oidcConfig } = await setupDomainForTest(uniqueName('pre-registration', true), {
    accessToken,
    waitForStart: true,
  });

  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;

  const appClientId = uniqueName('preregapp', true);
  const app = await createApplication(domain.id, accessToken, {
    name: appClientId,
    type: 'WEB',
    clientId: appClientId,
    redirectUris: ['https://callback'],
  });
  const updatedApp = await waitForSyncAfter(
    domain.id,
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
    ),
  );
  // restore the clientSecret coming from the create order
  updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
  const application = updatedApp;

  return {
    domain: domain,
    oidc: oidcConfig,
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
