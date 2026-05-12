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

import { expect } from '@jest/globals';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';

/** Minimal fixture shape required to execute the CIMD authorization code flow. */
export interface CimdAuthCodeFlowFixture {
  clientId: string;
  redirectUri: string;
  oidc: { authorization_endpoint: string };
  user: { username: string; password: string };
}

/**
 * Simulates a full browser-based authorization code flow for a URL-shaped CIMD client.
 * Handles the login form and, if present, the consent screen.
 * Returns the authorization code extracted from the final redirect location.
 */
export async function executeCimdAuthCodeFlow(fixture: CimdAuthCodeFlowFixture): Promise<string> {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: fixture.clientId,
    redirect_uri: fixture.redirectUri,
    scope: 'openid',
    state: 'cimd-state',
  });

  const authResponse = await performGet(`${fixture.oidc.authorization_endpoint}?${params.toString()}`).expect(302);
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
