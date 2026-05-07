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
import {
  extractXsrfTokenAndActionResponse,
  performFormPost,
  performGet,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { setup } from '../../test-fixture';
import { CimdRefreshTokenFixture, setupCimdRefreshTokenFixture } from './fixtures/cimd-refresh-token-fixture';

setup(200000);

/**
 * Browser login for a URL-shaped CIMD client; returns the authorization code.
 */
async function executeCimdAuthCodeFlow(fixture: CimdRefreshTokenFixture): Promise<string> {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: fixture.clientId,
    redirect_uri: fixture.redirectUri,
    scope: 'openid',
    state: 'cimd-refresh-state',
  });
  const authorizeUrl = `${fixture.oidc.authorization_endpoint}?${params.toString()}`;

  const authResponse = await performGet(authorizeUrl).expect(302);
  const { headers, token, action } = await extractXsrfTokenAndActionResponse(authResponse);

  const postLogin = await performFormPost(
    action,
    '',
    {
      'X-XSRF-TOKEN': token,
      username: fixture.user.username,
      password: fixture.user.password,
      client_id: fixture.clientId,
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  let redirectResponse = await performGet(postLogin.headers['location'], '', {
    Cookie: postLogin.headers['set-cookie'],
  }).expect(302);

  if (redirectResponse.headers['location']?.includes('/oauth/consent')) {
    const consentResult = await extractXsrfTokenAndActionResponse(redirectResponse);
    const postConsent = await performFormPost(
      consentResult.action,
      '',
      {
        'X-XSRF-TOKEN': consentResult.token,
        'scope.openid': true,
        user_oauth_approval: true,
      },
      {
        Cookie: consentResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    redirectResponse = await performGet(postConsent.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);
  }

  const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
  expect(redirectResponse.headers['location']).toMatch(codePattern);
  return redirectResponse.headers['location'].match(codePattern)![1];
}

let fixture: CimdRefreshTokenFixture;

beforeAll(async () => {
  fixture = await setupCimdRefreshTokenFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD refresh token', () => {
  it('should issue refresh_token on code exchange and accept refresh grant with client_id in body', async () => {
    const code = await executeCimdAuthCodeFlow(fixture);

    const codeExchangeBody = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: fixture.redirectUri,
      client_id: fixture.clientId,
    });

    const tokenResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      codeExchangeBody.toString(),
      {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    expect(tokenResponse.body.access_token).toMatch(JWT_FORMAT);
    expect(tokenResponse.body.token_type).toEqual('bearer');
    expect(tokenResponse.body.refresh_token).toMatch(JWT_FORMAT);

    const firstAccessToken = tokenResponse.body.access_token;
    const refreshBody = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: tokenResponse.body.refresh_token,
      client_id: fixture.clientId,
    });

    const refreshResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      refreshBody.toString(),
      {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    expect(refreshResponse.body.access_token).toMatch(JWT_FORMAT);
    expect(refreshResponse.body.access_token).not.toEqual(firstAccessToken);
  });

  it('should issue refresh_token on code exchange and accept refresh grant with url-encoded client_id in Basic auth', async () => {
    const code = await executeCimdAuthCodeFlow(fixture);
    const authHeader = 'Basic ' + getBase64BasicAuth(encodeURIComponent(fixture.clientId), '');

    const codeExchangeBody = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: fixture.redirectUri,
    });

    const tokenResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      codeExchangeBody.toString(),
      {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: authHeader,
      },
    ).expect(200);

    expect(tokenResponse.body.access_token).toMatch(JWT_FORMAT);
    expect(tokenResponse.body.token_type).toEqual('bearer');
    expect(tokenResponse.body.refresh_token).toMatch(JWT_FORMAT);

    const firstAccessToken = tokenResponse.body.access_token;
    const refreshBody = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: tokenResponse.body.refresh_token,
    });

    const refreshResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      refreshBody.toString(),
      {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: authHeader,
      },
    ).expect(200);

    expect(refreshResponse.body.access_token).toMatch(JWT_FORMAT);
    expect(refreshResponse.body.access_token).not.toEqual(firstAccessToken);
  });

  it('should reject refresh without client_id', async () => {
    const code = await executeCimdAuthCodeFlow(fixture);

    const tokenResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        redirect_uri: fixture.redirectUri,
        client_id: fixture.clientId,
      }).toString(),
      {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const refreshBody = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: tokenResponse.body.refresh_token,
    });

    const refreshResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      refreshBody.toString(),
      {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(401);

    expect(refreshResponse.body.error).toEqual('invalid_client');
  });
});
