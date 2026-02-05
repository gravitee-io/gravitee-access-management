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
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { DownstreamDomain, UserData } from './account-link-fixture';

export const setupDownstreamDomain = async (
  accessToken: string,
  domainName: string,
  userData: UserData,
  allowedRedirectUri: string,
): Promise<DownstreamDomain> => {
  const oidcDomain = await createDomain(accessToken, domainName, 'Downstream domain').then((domain) =>
    patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
        },
      },
    }),
  );
  const idpSet = await getAllIdps(oidcDomain.id, accessToken);

  const oidcApp = await createApplication(oidcDomain.id, accessToken, {
    name: 'oidc-app',
    type: 'WEB',
    clientId: 'oidc-app',
    clientSecret: 'oidc-app',
    redirectUris: ['http://localhost:8092/account-linking-domain/login/callback'],
  }).then((app) =>
    updateApplication(
      oidcDomain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [allowedRedirectUri],
            scopeSettings: [
              {
                scope: 'openid',
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

  await createUser(oidcDomain.id, accessToken, userData);

  await startDomain(oidcDomain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(oidcDomain);
  await waitForDomainSync(oidcDomain.id, accessToken);

  return {
    oidcDomain: oidcDomain,
    oidcApplication: oidcApp,
    oidcClientId: oidcApp.settings.oauth.clientId,
    oidcClientSecret: oidcApp.settings.oauth.clientSecret,
    oidc: domainWithOidc.oidcConfig,
    user: userData,
    cleanUp: async () => {
      if (oidcDomain?.id && accessToken) {
        await safeDeleteDomain(oidcDomain.id, accessToken);
      }
    },
  };
};
