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

import fetch from 'cross-fetch';

import * as faker from 'faker';
import { jest, afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { getAllUsers, listUsers } from '@management-commands/user-management-commands';
import { extractXsrfToken, getWellKnownOpenIdConfiguration, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { createIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { decodeJwt } from '@utils-commands/jwt';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

const jdbc = process.env.GRAVITEE_REPOSITORIES_MANAGEMENT_TYPE;

let domain;
let accessToken;
let defaultIdp;
let customIdp;
let application;
let clientId;
let openIdConfiguration;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  domain = await createDomain(accessToken, uniqueName('user-registration', true), faker.company.catchPhraseDescriptor());
  expect(domain).toBeDefined();

  await startDomain(domain.id, accessToken);

  // Get the default idp and create a new one.
  const idpSet = await getAllIdps(domain.id, accessToken);
  defaultIdp = idpSet.values().next().value;
  customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);

  // Create the application
  application = await createApplication(domain.id, accessToken, {
    name: faker.database.column.name,
    type: 'WEB',
    clientId: faker.random.alphaNumeric,
    clientSecret: faker.random.alphaNumeric,
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
            registerEnabled: true,
          },
        },
        identityProviders: [
          { identity: defaultIdp.id, priority: -1 },
          { identity: customIdp.id, priority: -1 },
        ],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();
  clientId = application.settings.oauth.clientId;

  await waitForDomainStart(domain).then((started) => {
    domain = started.domain;
    openIdConfiguration = started.oidcConfig;
    expect(openIdConfiguration).toBeDefined();
  });
});

describe('Register User on domain', () => {
  it("Domain shouldn't have users", async () => {
    const usersPage = await getAllUsers(domain.id, accessToken);
    expect(usersPage).toBeDefined();
    expect(usersPage.totalCount).toEqual(0);
  });

  describe('User', () => {
    it('Should not be able to register with invalid username', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: '$£êê',
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };
      await register(user, 'warning=invalid_user_information');

      const usersPage = await getAllUsers(domain.id, accessToken);
      expect(usersPage).toBeDefined();
      expect(usersPage.totalCount).toEqual(0);
    });

    it('Should not be able to register with invalid email address', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.random.word(),
        password: 'P@ssw0rd!123',
      };
      await register(user, 'warning=invalid_email');

      const usersPage = await getAllUsers(domain.id, accessToken);
      expect(usersPage).toBeDefined();
      expect(usersPage.totalCount).toEqual(0);
    });

    it('Should not be able to register with invalid password', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: '1234',
      };
      await register(user, 'warning=invalid_password_value');

      const usersPage = await getAllUsers(domain.id, accessToken);
      expect(usersPage).toBeDefined();
      expect(usersPage.totalCount).toEqual(0);
    });

    it('Should be able to register with valid information', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };
      await register(user, 'success=registration_succeed');

      const foundUser = await listUsers(domain.id, accessToken, user.username);
      expect(foundUser).toBeDefined();
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0]).toBeDefined();
      expect(foundUser.data[0].source).toEqual(defaultIdp.name);
    });
  });

  describe('User Account update', () => {
    it('Application update to use Custom IDP as defaulf', async () => {
      await updateApplication(
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
        },
        application.id,
      );

      await waitForDomainSync(domain.id, accessToken);
    });

    it('When a user register, he should be linked to the custom idp', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };
      await register(user, 'success=registration_succeed');

      const foundUser = await listUsers(domain.id, accessToken, user.username);
      expect(foundUser).toBeDefined();
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0]).toBeDefined();
      expect(foundUser.data[0].source).toEqual(customIdp.name);
    });

    it('Application update to preserve user session and redirect to specific URL', async () => {
      await updateApplication(
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
        },
        application.id,
      );

      await waitForDomainSync(domain.id, accessToken);
    });

    it('When a user register, he should have userId into the session and be redirected to specific URL', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };

      await register(user, `https://acustom/web/site?client_id=${clientId}`, true);

      const foundUser = await listUsers(domain.id, accessToken, user.username);
      expect(foundUser).toBeDefined();
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0]).toBeDefined();
      expect(foundUser.data[0].source).toEqual(customIdp.name);
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

const createMongoIdp = async (domainId, accessToken) => {
  console.log('creating mongodb  idp');
  return await createIdp(domainId, accessToken, {
    external: false,
    type: 'mongo-am-idp',
    domainWhitelist: [],
    configuration:
      '{"uri":"mongodb://localhost:27017","host":"localhost","port":27017,"enableCredentials":false,"databaseCredentials":"gravitee-am","database":"gravitee-am","usersCollection":"idp-test-users","findUserByUsernameQuery":"{$or: [{username: ?}, {contract: ?}]}","findUserByEmailQuery":"{email: ?}","usernameField":"username","passwordField":"password","passwordEncoder":"None","useDedicatedSalt":false,"passwordSaltLength":32}',
    name: 'another-idp',
  });
};

const createJdbcIdp = async (domainId, accessToken) => {
  console.log('creating jdbc idp');
  const password = process.env.GRAVITEE_REPOSITORIES_OAUTH2_JDBC_PASSWORD
    ? process.env.GRAVITEE_REPOSITORIES_OAUTH2_JDBC_PASSWORD
    : 'p@ssw0rd';
  const database = process.env.GRAVITEE_REPOSITORIES_OAUTH2_JDBC_DATABASE
    ? process.env.GRAVITEE_REPOSITORIES_OAUTH2_JDBC_DATABASE
    : 'gravitee-am';

  return await createIdp(domainId, accessToken, {
    external: false,
    type: 'jdbc-am-idp',
    domainWhitelist: [],
    configuration: `{\"host\":\"localhost\",\"port\":5432,\"protocol\":\"postgresql\",\"database\":\"${database}\",\"usersTable\":\"test_users\",\"user\":\"postgres\",\"password\":\"${password}\",\"autoProvisioning\":\"true\",\"selectUserByUsernameQuery\":\"SELECT * FROM test_users WHERE username = %s\",\"selectUserByMultipleFieldsQuery\":\"SELECT * FROM test_users WHERE username = %s or email = %s\",\"selectUserByEmailQuery\":\"SELECT * FROM test_users WHERE email = %s\",\"identifierAttribute\":\"id\",\"usernameAttribute\":\"username\",\"emailAttribute\":\"email\",\"passwordAttribute\":\"password\",\"passwordEncoder\":\"None\",\"useDedicatedSalt\":false,\"passwordSaltLength\":32}`,
    name: 'other-jdbc-idp',
  });
};

const register = async (user, expected, sessionActive = false) => {
  const uri = `/${domain.hrid}/register?client_id=${clientId}`;
  const { headers, token: xsrfToken } = await extractXsrfToken(process.env.AM_GATEWAY_URL, uri);

  //Submit forgot password form
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
