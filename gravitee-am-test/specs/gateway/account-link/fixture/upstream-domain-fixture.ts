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
import { createIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { DownstreamDomain, UpstreamDomain, UserData } from './account-link-fixture';
import { createUser } from '@management-commands/user-management-commands';

export const setupUpstreamDomain = async (
  accessToken: string,
  domainName: string,
  downStreamDomain: DownstreamDomain,
  user: UserData,
): Promise<UpstreamDomain> => {
  const domain = await createDomain(accessToken, domainName, 'Upstream domain').then((domain) =>
    patchDomain(domain.id, accessToken, {
      accountSettings: {
        maxLoginAttempts: 3,
        loginAttemptsDetectionEnabled: true,
        accountBlockedDuration: 180,
        loginAttemptsResetTime: 120,
      },
    }),
  );

  await createUser(domain.id, accessToken, user);

  const idpConfig = {
    clientId: downStreamDomain.oidcClientId,
    clientSecret: downStreamDomain.oidcClientSecret,
    clientAuthenticationMethod: 'client_secret_basic',
    wellKnownUri: `http://localhost:8092/${downStreamDomain.oidcDomain.hrid}/oidc/.well-known/openid-configuration`,
    userAuthorizationUri: `http://localhost:8092/${downStreamDomain.oidcDomain.hrid}/oauth/authorize`,
    accessTokenUri: `http://localhost:8092/${downStreamDomain.oidcDomain.hrid}/oauth/token`,
    userProfileUri: `http://localhost:8092/${downStreamDomain.oidcDomain.hrid}/oidc/userinfo`,
    logoutUri: `http://localhost:8092/${downStreamDomain.oidcDomain.hrid}/logout`,
    responseType: 'code',
    responseMode: 'default',
    encodeRedirectUri: false,
    useIdTokenForUserInfo: false,
    signature: 'RSA_RS256',
    publicKeyResolver: 'GIVEN_KEY',
    connectTimeout: 10000,
    idleTimeout: 10000,
    maxPoolSize: 200,
    storeOriginalTokens: false,
  };
  const openIdIdp = {
    configuration: `${JSON.stringify(idpConfig)}`,
    name: 'oidc',
    type: 'oauth2-generic-am-idp',
    external: true,
  };
  const oidcIdp = await createIdp(domain.id, accessToken, openIdIdp);

  // Create an application with oidc provider
  const oidcIdpApp = await createApplication(domain.id, accessToken, {
    name: uniqueName('app', true),
    type: 'WEB',
    clientId: uniqueName('app', true),
    clientSecret: uniqueName('app', true),
    redirectUris: ['https://test.com'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        identityProviders: [{ identity: oidcIdp.id, priority: -1 }],
        settings: {
          login: {
            inherited: false,
            hideForm: true,
          },
        },
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  // Create an application with an internal provider
  const idpSetAccountLinkingDomain = await getAllIdps(domain.id, accessToken);
  const localIdp = idpSetAccountLinkingDomain
    .filter((idp) => idp.type === 'mongo-am-idp' || idp.type === 'jdbc-am-idp')
    .values()
    .next().value;
  const localIdpApp = await createApplication(domain.id, accessToken, {
    name: uniqueName('app-local', true),
    type: 'WEB',
    clientId: uniqueName('app-local', true),
    clientSecret: uniqueName('app-local', true),
    redirectUris: ['https://test.com'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        identityProviders: [{ identity: localIdp.id, priority: -1 }],
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
  await waitForDomainSync(domain.id);

  return {
    domain: domain,
    oidc: domainWithOidc.oidcConfig,
    oidcIdp: oidcIdp,
    localIdp: localIdp,
    localIdpApp: localIdpApp,
    oidcApp: oidcIdpApp,
    user: user,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
