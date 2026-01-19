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

import { afterAll, beforeAll, expect } from '@jest/globals';
import { createUser, updateUsername } from '@management-commands/user-management-commands';
import { logoutUser, performGet } from '@gateway-commands/oauth-oidc-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { LoginFlowFixture, setupFixture } from './fixture/login-flow-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: LoginFlowFixture;

beforeAll(async () => {
  fixture = await setupFixture();
  expect(fixture.openIdConfiguration).toBeDefined();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('multiple user', () => {
  const contractValue = '1234';

  it('all users should be able to login using username and password', async () => {
    const userPassword1 = fixture.uniquePassword();
    const commonPassword = fixture.uniquePassword();
    const userPassword2 = fixture.uniquePassword();
    const user1Name = uniqueName('john.doe', true);
    const user1 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'john',
      lastName: 'doe',
      email: `${user1Name}@test.com`,
      username: user1Name,
      password: userPassword1,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user1).toBeDefined();

    const user2Name = uniqueName('jensen.barbara', true);
    const user2 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'jensen',
      lastName: 'barbara',
      username: user2Name,
      email: `${user2Name}@test.com`,
      password: commonPassword,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user2).toBeDefined();

    const user3Name = uniqueName('flip.flop', true);
    const user3 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'flip',
      lastName: 'flop',
      username: user3Name,
      email: `${user3Name}@test.com`,
      password: commonPassword,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user3).toBeDefined();

    const user4Name = uniqueName('some.user', true);
    const user4 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'some',
      lastName: 'user',
      username: user4Name,
      email: `${user4Name}@test.com`,
      password: userPassword2,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user4).toBeDefined();

    const clientId = fixture.multiUserLoginApp.settings.oauth.clientId;
    const user1TokenResponse = await loginUserNameAndPassword(
      clientId,
      user1,
      userPassword1,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(user1TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, user1TokenResponse);

    const user2TokenResponse = await loginUserNameAndPassword(
      clientId,
      user2,
      commonPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(user2TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, user2TokenResponse);

    //user3 has same password as user2
    const user3TokenResponse = await loginUserNameAndPassword(
      clientId,
      user3,
      commonPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(user3TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, user3TokenResponse);

    const user4TokenResponse = await loginUserNameAndPassword(
      clientId,
      user4,
      userPassword2,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(user4TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, user4TokenResponse);
  });

  it('user should have their username changed and have their session/token canceled', async () => {
    const userPassword = fixture.uniquePassword();
    const userName = uniqueName('john.doe', true);
    let user = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'john',
      lastName: 'doe',
      email: `${userName}@test.com`,
      username: userName,
      password: userPassword,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user).toBeDefined();

    const clientId = fixture.multiUserLoginApp.settings.oauth.clientId;
    let userTokenResponse = await loginUserNameAndPassword(
      clientId,
      user,
      userPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(userTokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, userTokenResponse);

    user = await updateUsername(fixture.domain.id, fixture.accessToken, user.id, user.username + '-changed');
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;

    const authResponse = await performGet(fixture.openIdConfiguration.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];
    expect(loginLocation).not.toContain(`callback?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, userTokenResponse);

    userTokenResponse = await loginUserNameAndPassword(clientId, user, userPassword, false, fixture.openIdConfiguration, fixture.domain);
    expect(userTokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, userTokenResponse);
  });

  it('should throw exception user name and wrong password', async () => {
    const userPassword = fixture.uniquePassword();
    const commonPassword = fixture.uniquePassword();
    const wrongPassword = 'WrongPassword';
    const clientId = fixture.multiUserLoginApp.settings.oauth.clientId;

    const user1Name = uniqueName('john.doe', true);
    const user1 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'john',
      lastName: 'doe',
      email: `${user1Name}@test.com`,
      username: user1Name,
      password: userPassword,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user1).toBeDefined();

    const failedLoginResponse1 = await loginUserNameAndPassword(
      clientId,
      user1,
      wrongPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(failedLoginResponse1.headers['location']).toContain(
      `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`,
    );

    const user2Name = uniqueName('jensen.barbara', true);
    const user2 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'jensen',
      lastName: 'barbara',
      username: user2Name,
      email: `${user2Name}@test.com`,
      password: commonPassword,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user2).toBeDefined();

    const failedLoginResponse2 = await loginUserNameAndPassword(
      clientId,
      user2,
      wrongPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(failedLoginResponse2.headers['location']).toContain(
      `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`,
    );

    const user3Name = uniqueName('flip.flop', true);
    const user3 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'flip',
      lastName: 'flop',
      username: user3Name,
      email: `${user3Name}@test.com`,
      password: commonPassword,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user3).toBeDefined();

    const failedLoginResponse3 = await loginUserNameAndPassword(
      clientId,
      user3,
      wrongPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(failedLoginResponse3.headers['location']).toContain(
      `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+us`,
    );
  });
});
