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
import { afterAll, beforeAll } from '@jest/globals';
import { assertGeneratedToken, OAuth2Fixture, setupFixture } from './fixture/oauth2-fixture';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token, getBase64BasicAuth } from '@gateway-commands/utils';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: OAuth2Fixture;
let user: {
  username: string;
  password: string;
};

beforeAll(async () => {
  fixture = await setupFixture({
    withOpenidScope: true,
    type: 'WEB',
    grantTypes: ['password'],
  });
  user = fixture.users[0];
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 6746 - Resource Owner Password Credential Grant', () => {
  it('must perform an invalid client token request - base64 error', async () => {
    await performPost(fixture.oidc.token_endpoint, '', 'grant_type=password&username=admin&password=adminadmin&scope=openid', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic invalid`,
    }).expect(401);
  });

  it('must perform an invalid client token request', async () => {
    await performPost(fixture.oidc.token_endpoint, '', 'grant_type=password&username=admin&password=adminadmin&scope=openid', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + getBase64BasicAuth('wrong-client-id', 'wrong-secret-id'),
    }).expect(401);
  });

  it('must perform a no scope token request', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${user.username}&password=${user.password}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    ).expect(200);

    const data = response.body;

    assertGeneratedToken(data, `openid ${fixture.scope.key}`);
  });

  it('must perform a an invalid scope token request', async () => {
    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${user.username}&password=${user.password}&scope=unknown`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    ).expect(400, {
      error: 'invalid_scope',
      error_description: 'Invalid scope(s): unknown',
    });
  });

  it('must perform a an empty scope token request', async () => {
    await performPost(fixture.oidc.token_endpoint, '', `grant_type=password&username=${user.username}&password=${user.password}&scope=`, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    }).expect(200);
  });

  it('must perform an invalid user token request', async () => {
    await performPost(fixture.oidc.token_endpoint, '', 'grant_type=password&username=admin&password=adminadmin&scope=openid', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    }).expect(400, {
      error: 'invalid_grant',
      error_description: 'The credentials entered are invalid',
    });
  });

  it('must generate a valid token for application1', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${user.username}&password=${user.password}&scope=${fixture.scope.key}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    ).expect(200);

    assertGeneratedToken(response.body, `${fixture.scope.key}`);
  });

  it('must generate a valid token for application2', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${user.username}&password=${user.password}&scope=${fixture.scope.key}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    ).expect(200);

    assertGeneratedToken(response.body, `${fixture.scope.key}`);
  });
});
