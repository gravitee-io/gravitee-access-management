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
import { createUser } from '@management-commands/user-management-commands';
import { loginAdditionalInfoAndPassword } from '@gateway-commands/login-commands';
import { logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { LoginFlowFixture, setupFixture } from './fixture/login-flow-fixture';
import { setup } from '../../test-fixture';

setup(200000);

const isMongo = process.env.REPOSITORY_TYPE !== 'jdbc';

let fixture: LoginFlowFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const describeMongo = isMongo ? describe : describe.skip;

describeMongo('Mongo: multiple user', () => {
  const contractValue = '1234';

  it('both users should be able to login using additional information (contract) and password', async () => {
    const user1Password = fixture.uniquePassword();
    const user2Password = fixture.uniquePassword();
    const user1Name = uniqueName('john.doe', true);
    const user1 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'john',
      lastName: 'doe',
      email: `${user1Name}@test.com`,
      username: user1Name,
      password: user1Password,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user1).toBeDefined();

    const user2Name = uniqueName('some.user', true);
    const user2 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'some',
      lastName: 'user',
      username: user2Name,
      email: `${user2Name}@test.com`,
      password: user2Password,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user2).toBeDefined();

    const clientId = fixture.multiUserLoginApp.settings.oauth.clientId;

    //log in user1
    const user1TokenResponse = await loginAdditionalInfoAndPassword(
      clientId,
      contractValue,
      user1Password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(user1TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, user1TokenResponse);

    //log in user2
    const user2TokenResponse = await loginAdditionalInfoAndPassword(
      clientId,
      contractValue,
      user2Password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, user2TokenResponse);
  });

  it('should throw exception when more than one users have same password for login with additional information', async () => {
    const commonPassword = fixture.uniquePassword();
    const user1Name = uniqueName('jensen.barbara', true);
    const user1 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'jensen',
      lastName: 'barbara',
      username: user1Name,
      email: `${user1Name}@test.com`,
      password: commonPassword,
      client: fixture.multiUserLoginApp.id,
      source: fixture.customIdp.id,
      additionalInformation: {
        contract: contractValue,
      },
      preRegistration: false,
    });
    expect(user1).toBeDefined();

    const user2Name = uniqueName('flip.flop', true);
    const user2 = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'flip',
      lastName: 'flop',
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

    const clientId = fixture.multiUserLoginApp.settings.oauth.clientId;
    const failedLoginResponse = await loginAdditionalInfoAndPassword(
      clientId,
      contractValue,
      commonPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );
    expect(failedLoginResponse.headers['location']).toContain(
      `error=login_failed&error_code=invalid_user&error_description=Invalid+or+unknown+user`,
    );
  });
});
