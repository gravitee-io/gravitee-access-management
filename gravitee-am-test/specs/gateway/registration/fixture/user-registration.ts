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
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createCustomIdp } from '@utils-commands/idps-commands';
import faker from 'faker';
import { expect } from '@jest/globals';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { extractXsrfToken, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { decodeJwt } from '@utils-commands/jwt';
import { User } from '@management-models/User';
import { Fixture } from '../../../test-fixture';

export interface UserRegistrationFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  applicationWithCustomIdp: Application;
  applicationWithCustomIdpAndRedirect: Application;
  defaultIdp: IdentityProvider;
  customIdp: IdentityProvider;
}

export const setupFixture = async (): Promise<UserRegistrationFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('user-registration', true), 'Description');
  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;
  const customIdp = await createCustomIdp(domain.id, accessToken);
  const application = await createApp(domain.id, accessToken, {
    settings: {
      oauth: {
        redirectUris: ['https://callback'],
        grantTypes: ['authorization_code'],
      },
      login: {
        inherited: false,
        registerEnabled: true,
      },
    },
    identityProviders: [
      { identity: defaultIdp.id, priority: -1 },
      { identity: customIdp.id, priority: -1 },
    ],
  });

  const applicationWithCustomIdp = await createApp(domain.id, accessToken, {
    settings: {
      oauth: {
        redirectUris: ['https://callback'],
        grantTypes: ['authorization_code'],
      },
      login: {
        inherited: false,
        registerEnabled: true,
      },
      account: {
        inherited: false,
        defaultIdentityProviderForRegistration: customIdp.id,
      },
    },
    identityProviders: [
      { identity: defaultIdp.id, priority: -1 },
      { identity: customIdp.id, priority: -1 },
    ],
  });

  const applicationWithCustomIdpAndRedirect = await createApp(domain.id, accessToken, {
    settings: {
      oauth: {
        redirectUris: ['https://callback'],
        grantTypes: ['authorization_code'],
      },
      login: {
        inherited: false,
        registerEnabled: true,
      },
      account: {
        inherited: false,
        defaultIdentityProviderForRegistration: customIdp.id,
        autoLoginAfterRegistration: true,
        redirectUriAfterRegistration: 'https://acustom/web/site',
      },
    },
    identityProviders: [
      { identity: defaultIdp.id, priority: -1 },
      { identity: customIdp.id, priority: -1 },
    ],
  });

  await startDomain(domain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(domain);

  return {
    domain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    application: application,
    applicationWithCustomIdp: applicationWithCustomIdp,
    applicationWithCustomIdpAndRedirect: applicationWithCustomIdpAndRedirect,
    customIdp: customIdp,
    defaultIdp: defaultIdp,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

export const createApp = async (domainId: string, accessToken: string, settings: any): Promise<Application> => {
  return await createApplication(domainId, accessToken, {
    name: faker.database.column.name,
    type: 'WEB',
    clientId: faker.random.alphaNumeric,
    clientSecret: faker.random.alphaNumeric,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(domainId, accessToken, settings, app.id).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
};

export const register = async (domain: Domain, user: User, expected: string, clientId: string, sessionActive = false) => {
  const uri = `/${domain.hrid}/register?client_id=${clientId}`;
  const { headers, token: xsrfToken } = await extractXsrfToken(process.env.AM_GATEWAY_URL, uri);
  //Submit forgot password formf
  const postResponse = await performFormPost(
    process.env.AM_GATEWAY_URL,
    uri,
    {
      'X-XSRF-TOKEN': xsrfToken,
      firstName: user.firstName,
      lastName: user.lastName,
      username: user.username,
      email: user.email,
      password: user.password,
      client_id: clientId,
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
  expect(postResponse.headers['location']).toContain(expected);

  const cookies = postResponse.headers['set-cookie'];
  expect(cookies).toBeDefined();
  cookies
    .filter((entry) => entry.startsWith('GRAVITEE_IO_AM_SESSION'))
    .map((gioSessionCookie) => gioSessionCookie.substring(gioSessionCookie.indexOf('='), gioSessionCookie.indexOf(';') - 1))
    .map((jwtSession) => {
      const JWT = decodeJwt(jwtSession);
      sessionActive ? expect(JWT.userId).toBeDefined() : expect(JWT.userId).not.toBeDefined();
    });
};
