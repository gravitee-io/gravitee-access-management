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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { createUser, updateUsername } from '@management-commands/user-management-commands';
import { logoutUser, performGet } from '@gateway-commands/oauth-oidc-commands';
import { loginAdditionalInfoAndPassword, loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { createJdbcIdp, createMongoIdp } from '@utils-commands/idps-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';

globalThis.fetch = fetch;

const jdbc = process.env.REPOSITORY_TYPE;

let accessToken;
let domain;
let customIdp;
let multiUserLoginApp;
let openIdConfiguration;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await createDomain(accessToken, uniqueName('login-flow-domain'), 'test user login').then((domain) =>
    startDomain(domain.id, accessToken),
  );

  customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);
  multiUserLoginApp = await createTestApp('multi-user-login-app', domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
        scopeSettings: [
          { scope: 'openid', defaultScope: true },
          {
            scope: 'openid',
            defaultScope: true,
          },
        ],
      },
    },
    identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
  });

  await waitForDomainStart(domain).then((started) => {
    expect(started.oidcConfig).toBeDefined();
    openIdConfiguration = started.oidcConfig;
  });
});

describe('multiple user', () => {
  const contractValue = '1234';
  let user1;
  const user1Password = 'ZxcPrm7123!!';
  let user2;
  const commonPassword = 'AsdPrm7123!!';
  let commonEmail: string;
  let user3; //user3 has same password as user2
  let user4;
  const user4Password = 'QwePrm7123!!';
  let user5;
  let user6;
  const secondCommonPassword = 'PhdPrm7123!!';
  let secondCommonEmail: string;
  let testJohnDoe: string;
  let testJensenBarbara: string;
  let userFlipFlop: string;
  let testSomeUser: string;
  let testUserFive: string;
  let testUserSix: string;

  beforeAll(async () => {
    // Generate unique usernames to avoid conflicts in parallel execution
    testJohnDoe = uniqueName('john.doe', true);
    testJensenBarbara = uniqueName('jensen.barbara', true);
    userFlipFlop = uniqueName('flip.flop', true);
    testSomeUser = uniqueName('some.user', true);
    testUserFive = uniqueName('user.five', true);
    testUserSix = uniqueName('user.six', true);
    commonEmail = `${uniqueName('common', true)}@test.com`;
    secondCommonEmail = `${uniqueName('second.common', true)}@test.com`;

    user1 = await createUser(domain.id, accessToken, {
      firstName: 'john',
      lastName: 'doe',
      email: `${testJohnDoe}@test.com`,
      username: testJohnDoe,
      password: user1Password,
      client: multiUserLoginApp.id,
      source: customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });

    expect(user1).toBeDefined();

    user2 = await createUser(domain.id, accessToken, {
      firstName: 'jensen',
      lastName: 'barbara',
      username: testJensenBarbara,
      email: `${testJensenBarbara}@test.com`,
      password: commonPassword,
      client: multiUserLoginApp.id,
      source: customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });

    expect(user2).toBeDefined();

    user3 = await createUser(domain.id, accessToken, {
      firstName: 'flip',
      lastName: 'flop',
      username: userFlipFlop,
      email: commonEmail,
      password: commonPassword,
      client: multiUserLoginApp.id,
      source: customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });

    expect(user3).toBeDefined();

    user4 = await createUser(domain.id, accessToken, {
      firstName: 'some',
      lastName: 'user',
      username: testSomeUser,
      email: commonEmail,
      password: user4Password,
      client: multiUserLoginApp.id,
      source: customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });

    expect(user4).toBeDefined();

    user5 = await createUser(domain.id, accessToken, {
      firstName: 'alan',
      lastName: 'bull',
      username: testUserFive,
      email: secondCommonEmail,
      password: secondCommonPassword,
      client: multiUserLoginApp.id,
      source: customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });

    expect(user5).toBeDefined();

    user6 = await createUser(domain.id, accessToken, {
      firstName: 'james',
      lastName: 'hen',
      username: testUserSix,
      email: secondCommonEmail,
      password: secondCommonPassword,
      client: multiUserLoginApp.id,
      source: customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });

    expect(user6).toBeDefined();
  });

  it('all users should be able to login using username and password', async () => {
    const clientId = multiUserLoginApp.settings.oauth.clientId;
    const user1TokenResponse = await loginUserNameAndPassword(clientId, user1, user1Password, false, openIdConfiguration, domain);
    expect(user1TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

    const user2TokenResponse = await loginUserNameAndPassword(clientId, user2, commonPassword, false, openIdConfiguration, domain);
    expect(user2TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(openIdConfiguration.end_session_endpoint, user2TokenResponse);

    //user3 has same password as user2
    const user3TokenResponse = await loginUserNameAndPassword(clientId, user3, commonPassword, false, openIdConfiguration, domain);
    expect(user3TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(openIdConfiguration.end_session_endpoint, user3TokenResponse);

    const user4TokenResponse = await loginUserNameAndPassword(clientId, user4, user4Password, false, openIdConfiguration, domain);
    expect(user4TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(openIdConfiguration.end_session_endpoint, user4TokenResponse);
  });

  if (jdbc === 'jdbc') {
    console.log('executing jdbc specific test');
    it('jdbc: both users should be able to login using additional information (email) and password', async () => {
      const clientId = multiUserLoginApp.settings.oauth.clientId;
      //log in user3
      const user3TokenResponse = await loginAdditionalInfoAndPassword(
        clientId,
        commonEmail,
        commonPassword,
        false,
        openIdConfiguration,
        domain,
      );
      expect(user3TokenResponse.headers['location']).toContain('callback?code=');
      await logoutUser(openIdConfiguration.end_session_endpoint, user3TokenResponse);

      //log in user4
      const user4TokenResponse = await loginAdditionalInfoAndPassword(
        clientId,
        commonEmail,
        user4Password,
        false,
        openIdConfiguration,
        domain,
      );
      await logoutUser(openIdConfiguration.end_session_endpoint, user4TokenResponse);
    });

    it('jdbc: should throw exception when more than one users have same password for login with additional information', async () => {
      const clientId = multiUserLoginApp.settings.oauth.clientId;
      const failedLoginResponse = await loginAdditionalInfoAndPassword(
        clientId,
        secondCommonEmail,
        secondCommonPassword,
        false,
        openIdConfiguration,
        domain,
      );
      expect(failedLoginResponse.headers['location']).toContain(
        `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`,
      );
    });
  } else {
    console.log('executing mongodb specific test');
    it('mongo: both users should be able to login using additional information (contract) and password', async () => {
      const clientId = multiUserLoginApp.settings.oauth.clientId;

      //log in user1
      const user1TokenResponse = await loginAdditionalInfoAndPassword(
        clientId,
        contractValue,
        user1Password,
        false,
        openIdConfiguration,
        domain,
      );
      expect(user1TokenResponse.headers['location']).toContain('callback?code=');
      await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

      //log in user4
      const user4TokenResponse = await loginAdditionalInfoAndPassword(
        clientId,
        contractValue,
        user4Password,
        false,
        openIdConfiguration,
        domain,
      );
      await logoutUser(openIdConfiguration.end_session_endpoint, user4TokenResponse);
    });

    it('mongo: should throw exception when more than one users have same password for login with additional information', async () => {
      const clientId = multiUserLoginApp.settings.oauth.clientId;
      const failedLoginResponse = await loginAdditionalInfoAndPassword(
        clientId,
        contractValue,
        commonPassword,
        false,
        openIdConfiguration,
        domain,
      );
      expect(failedLoginResponse.headers['location']).toContain(
        `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`,
      );
    });
  }

  it('user should have their username changed and have their session/token canceled', async () => {
    const clientId = multiUserLoginApp.settings.oauth.clientId;
    let user1TokenResponse = await loginUserNameAndPassword(clientId, user1, user1Password, false, openIdConfiguration, domain);
    expect(user1TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

    user1 = await updateUsername(domain.id, accessToken, user1.id, user1.username + '-changed');
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;

    const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];
    expect(loginLocation).not.toContain(`callback?code=`);
    await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);

    user1TokenResponse = await loginUserNameAndPassword(clientId, user1, user1Password, false, openIdConfiguration, domain);
    expect(user1TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);
  });

  it('should throw exception user name and wrong password', async () => {
    const wrongPassword = 'WrongPassword';
    const clientId = multiUserLoginApp.settings.oauth.clientId;

    const failedLoginResponse1 = await loginUserNameAndPassword(clientId, user1, wrongPassword, false, openIdConfiguration, domain);
    expect(failedLoginResponse1.headers['location']).toContain(
      `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`,
    );

    const failedLoginResponse2 = await loginUserNameAndPassword(clientId, user2, wrongPassword, false, openIdConfiguration, domain);
    expect(failedLoginResponse2.headers['location']).toContain(
      `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`,
    );

    const failedLoginResponse3 = await loginUserNameAndPassword(clientId, user3, wrongPassword, false, openIdConfiguration, domain);
    expect(failedLoginResponse3.headers['location']).toContain(
      `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+us`,
    );
  });

  afterAll(async () => {
    // Users are cleaned up when domain is deleted
  });
});

afterAll(async () => {
  if (domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
