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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { login, getHeaderLocation } from '@gateway-commands/login-commands';
import { performGet, requestToken, logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { IdpMapperFixture, setupIdpMapperFixture, JOHN, JANE, REDIRECT_URI } from './fixture/idp-mapper-fixture';

setup(200000);

let fixture: IdpMapperFixture;

beforeAll(async () => {
  fixture = await setupIdpMapperFixture();
  expect(fixture.openIdConfiguration).toBeDefined();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

async function authenticateAndGetUserinfo(user: { username: string; password: string }): Promise<any> {
  const clientId = fixture.app.settings.oauth.clientId;
  const authResponse = await performGet(
    fixture.openIdConfiguration.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email`,
  ).expect(302);

  const postLogin = await login(authResponse, user.username, clientId, user.password);
  const loginResponse = await getHeaderLocation(postLogin);
  expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);

  const tokenResponse = await requestToken(fixture.app, fixture.openIdConfiguration, loginResponse);
  expect(tokenResponse.status).toBe(200);
  const { access_token } = tokenResponse.body;
  expect(access_token).toBeDefined();

  const userInfoResponse = await performGet(fixture.openIdConfiguration.userinfo_endpoint, '', {
    Authorization: `Bearer ${access_token}`,
  }).expect(200);

  await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  return userInfoResponse.body;
}

describe('Simple attribute rename', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers({ given_name: 'firstname', family_name: 'lastname' }, {}, {});
  });

  it('should map attribute to claim (firstname → given_name) in userinfo', async () => {
    const claims = await authenticateAndGetUserinfo(JOHN);
    expect(claims.given_name).toBe(JOHN.firstname);
  });

  it('should map attribute to claim (lastname → family_name) in userinfo', async () => {
    const claims = await authenticateAndGetUserinfo(JOHN);
    expect(claims.family_name).toBe(JOHN.lastname);
  });
});

describe('Multiple mapper entries', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers(
      { given_name: 'firstname', family_name: 'lastname', preferred_username: 'username' },
      {},
      {},
    );
  });

  it('should include all mapped claims simultaneously in userinfo', async () => {
    const claims = await authenticateAndGetUserinfo(JOHN);
    expect(claims.given_name).toBe(JOHN.firstname);
    expect(claims.family_name).toBe(JOHN.lastname);
    expect(claims.preferred_username).toBe(JOHN.username);
  });
});

describe('Clearing mappers removes claim from userinfo', () => {
  it('should not grant mapped claim to users logging in after mapper is cleared', async () => {
    // Set mapper and verify it takes effect
    await fixture.updateIdpMappers({ nickname: 'username' }, {}, {});
    const claimsWithMapper = await authenticateAndGetUserinfo(JOHN);
    expect(claimsWithMapper.nickname).toBe(JOHN.username);

    // Clear mapper and verify the claim is absent (for first time login)
    await fixture.updateIdpMappers({}, {}, {});
    const claimsAfterClear = await authenticateAndGetUserinfo(JANE);
    expect(claimsAfterClear.nickname).toBeUndefined();
  });
});

describe('Mapper update applied at next login', () => {
  it('should apply mapper changes on the next login', async () => {
    // Without mapper: user has no stored nickname, claim should be absent
    await fixture.updateIdpMappers({}, {}, {});
    const claimsBefore = await authenticateAndGetUserinfo(JANE);
    expect(claimsBefore.nickname).toBeUndefined();

    // With mapper: user logs in and the mapped claim is present
    await fixture.updateIdpMappers({ nickname: 'username' }, {}, {});
    const claimsAfter = await authenticateAndGetUserinfo(JANE);
    expect(claimsAfter.nickname).toBe(JANE.username);
  });
});
