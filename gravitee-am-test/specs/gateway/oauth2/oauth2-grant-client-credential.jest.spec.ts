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
import { renewApplicationSecrets } from '@management-commands/application-management-commands';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: OAuth2Fixture;

beforeAll(async () => {
  fixture = await setupFixture({
    withOpenidScope: false,
    type: 'SERVICE',
    grantTypes: ['client_credentials'],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 6746 - Client credentials grant', () => {
  it('renew client - must generate a new token from application 3', async () => {
    const response = await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    });

    assertGeneratedToken(response.body, 'scope1');
  });

  it('renew client - must renew secrets and test token', async () => {
    const newSecret = await renewApplicationSecrets(
      fixture.masterDomain.id,
      fixture.accessToken,
      fixture.application.id,
      fixture.application.secrets[0].id,
    ).then(async (clintSecret) => {
      await waitForDomainSync(fixture.masterDomain.id, fixture.accessToken, { stabilityMillis: 5000 });
      await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      }).expect(401);

      const response = await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(fixture.application.settings.oauth.clientId, clintSecret.secret),
      }).expect(200);

      assertGeneratedToken(response.body, 'scope1');
      return clintSecret;
    });
    fixture.application.settings.oauth.clientSecret = newSecret.secret;
  });

  it('must perform invalid request', async () => {
    await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + getBase64BasicAuth(fixture.application.settings.oauth.clientId, 'wrong-secret-id'),
    }).expect(401);
  });

  it('must perform a no scope request', async () => {
    const response = await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    }).expect(200);

    assertGeneratedToken(response.body, 'scope1');
  });

  it('must perform an empty scope request', async () => {
    await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials&scope=', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    }).expect(200);
  });

  it('must generate token', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      'grant_type=client_credentials&scope=scope1&example_parameter=example_value',
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    ).expect(200);

    assertGeneratedToken(response.body, 'scope1');
  });
});
