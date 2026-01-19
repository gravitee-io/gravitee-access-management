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
import { uniqueName } from '@utils-commands/misc';
import { expect } from '@jest/globals';
import { createScope } from '@management-commands/scope-management-commands';
import { createIdp, deleteIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { createDomainCertificate } from './oauth2-cert-fixture';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { Application } from '@management-models/Application';
import { Scope } from '@management-models/Scope';
import { Fixture } from '../../../test-fixture';

export interface OAuth2Fixture extends Fixture {
  masterDomain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  anotherApplication: Application;
  scope: Scope;
  shortLivingScope: Scope;
  users: { username: string; password: string }[];
}

export interface OAuth2ApplicationSettings {
  type: 'WEB' | 'SERVICE';
  withOpenidScope: boolean;
  grantTypes: string[];
}

export const setupFixture = async (appSettings: OAuth2ApplicationSettings): Promise<OAuth2Fixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('oauth2', true), 'Description')
    .then((domain) => mustMakeDomainMaster(domain, accessToken))
    .then((domain) => createDomainCertificate(domain, accessToken).then(() => domain));

  const idp = await createNewIdp(domain, accessToken);
  const scope = await createApplicationScope(domain, accessToken, 'scope1');
  const shortLivingScope = await createApplicationScope(domain, accessToken, 'short', 2);

  const identityProviders = appSettings.type === 'SERVICE' ? undefined : [{ identity: idp.id, priority: -1 }];
  const scopeSettings = appSettings.withOpenidScope
    ? [
        { scope: scope.key, defaultScope: true },
        { scope: shortLivingScope.key, defaultScope: false },
        { scope: 'openid', defaultScope: true },
      ]
    : [
        { scope: scope.key, defaultScope: true },
        { scope: shortLivingScope.key, defaultScope: false },
      ];

  const app = await createApp(domain, accessToken, appSettings.type, scopeSettings, appSettings.grantTypes, identityProviders);
  const anotherApp = await createApp(domain, accessToken, appSettings.type, scopeSettings, appSettings.grantTypes, identityProviders);

  await startDomain(domain.id, accessToken);

  const domainWithOidc = await waitForDomainStart(domain);
  return {
    masterDomain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    application: app,
    anotherApplication: anotherApp,
    users: idp.users,
    scope: scope,
    shortLivingScope: shortLivingScope,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

async function mustMakeDomainMaster(createdDomain: Domain, accessToken: string): Promise<Domain> {
  const patchedDomain = await patchDomain(createdDomain.id, accessToken, {
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
  expect(patchedDomain).toBeDefined();
  expect(patchedDomain.id).toEqual(createdDomain.id);
  expect(patchedDomain.master).toBeTruthy();
  expect(patchedDomain.oidc.clientRegistrationSettings).toBeDefined();
  expect(patchedDomain.oidc.clientRegistrationSettings.allowLocalhostRedirectUri).toBeTruthy();
  expect(patchedDomain.oidc.clientRegistrationSettings.allowHttpSchemeRedirectUri).toBeTruthy();
  expect(patchedDomain.oidc.clientRegistrationSettings.allowWildCardRedirectUri).toBeTruthy();
  expect(patchedDomain.oidc.clientRegistrationSettings.dynamicClientRegistrationEnabled).toBeFalsy();
  expect(patchedDomain.oidc.clientRegistrationSettings.openDynamicClientRegistrationEnabled).toBeFalsy();

  return patchedDomain;
}

async function createApplicationScope(domain: Domain, accessToken: string, scopeName: string, expiresIn?: number) {
  const newScope = await createScope(domain.id, accessToken, {
    key: scopeName,
    name: scopeName,
    description: scopeName,
    expiresIn: expiresIn,
  });
  expect(newScope).toBeDefined();

  return newScope;
}

async function createNewIdp(domain: Domain, accessToken: string) {
  await mustDeleteDefaultIdp(domain, accessToken);
  const idpConfig = {
    users: [
      {
        firstname: 'my-user',
        lastname: 'my-user-lastname',
        username: uniqueName('joe.doe', true),
        password: '#CoMpL3X-P@SsW0Rd',
      },
      {
        firstname: 'Jensen',
        lastname: 'Barbara',
        username: uniqueName('jensen.barbara', true),
        email: `jensen.barbara@mail.com`,
        password: '#CoMpL3X-P@SsW0Rd',
      },
    ],
  };
  const newIdp = await createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify(idpConfig),
    name: 'inmemory',
  });
  expect(newIdp).toBeDefined();
  return {
    id: newIdp.id,
    users: idpConfig.users,
  };
}

async function mustDeleteDefaultIdp(domain: Domain, accessToken: string) {
  await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
  const idpSet = await getAllIdps(domain.id, accessToken);
  expect(idpSet.length).toEqual(0);
}

async function createApp(
  domain: Domain,
  accessToken: string,
  type: 'WEB' | 'SERVICE',
  scopeSettings: any[],
  grantTypes: string[],
  identityProviders?: any[],
) {
  const appName = uniqueName('my-client', true);
  const appClientId = uniqueName('clientId-test', true);
  const application = await createApplication(domain.id, accessToken, {
    name: appName,
    type: type,
    clientId: appClientId,
    redirectUris: ['http://localhost:4000/'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['http://localhost:4000/'],
            grantTypes: grantTypes,
            scopeSettings: scopeSettings,
          },
        },
        identityProviders: identityProviders,
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  expect(application).toBeDefined();
  return application;
}

export function assertGeneratedToken(data, scopes) {
  expect(data.access_token).toBeDefined();
  expect(data.token_type).toBeDefined();
  expect(data.token_type).toEqual('bearer');
  expect(data.expires_in).toBeDefined();
  if (scopes) {
    expect(data.scope).toBeDefined();
    expect(data.scope).toEqual(scopes);
  } else {
    expect(data.scope).not.toBeDefined();
  }
}
