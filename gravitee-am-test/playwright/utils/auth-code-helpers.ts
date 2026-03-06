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
import {
  extractXsrfTokenAndActionResponse,
  performFormPost,
  performGet,
  performPost,
} from '../../api/commands/gateway/oauth-oidc-commands';
import { SubjectTokens, OidcConfiguration } from './token-exchange-helpers';

/**
 * Performs a full authorization code flow via HTTP (no browser needed).
 * Ported from Jest revocation-fixture.ts.
 *
 * Steps: authorization request → login → optional consent → code → token exchange
 */
export async function obtainAuthorizationCodeTokens(
  oidc: OidcConfiguration,
  basicAuth: string,
  clientId: string,
  username: string,
  password: string,
  redirectUri: string,
  scope = 'openid%20profile%20offline_access',
): Promise<SubjectTokens> {
  const authorizationRequestParams =
    `?response_type=code&client_id=${clientId}` +
    `&redirect_uri=${encodeURIComponent(redirectUri)}` +
    `&scope=${scope}`;

  const authResponse = await performGet(oidc.authorization_endpoint, authorizationRequestParams).expect(302);
  const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

  const postLogin = await performFormPost(
    loginResult.action,
    '',
    {
      'X-XSRF-TOKEN': loginResult.token,
      username,
      password,
      client_id: clientId,
    },
    {
      Cookie: loginResult.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
    Cookie: postLogin.headers['set-cookie'],
  }).expect(302);

  let redirectWithCodeLocation = postLoginRedirect.headers['location'];

  // Handle consent page if present
  if (redirectWithCodeLocation.includes('/oauth/consent')) {
    const consentResult = await extractXsrfTokenAndActionResponse(postLoginRedirect);
    const postConsent = await performFormPost(
      consentResult.action,
      '',
      {
        'X-XSRF-TOKEN': consentResult.token,
        'scope.openid': true,
        'scope.profile': true,
        'scope.offline_access': true,
        user_oauth_approval: true,
      },
      {
        Cookie: consentResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    const postConsentRedirect = await performGet(postConsent.headers['location'], '', {
      Cookie: postConsent.headers['set-cookie'] || consentResult.headers['set-cookie'],
    }).expect(302);

    redirectWithCodeLocation = postConsentRedirect.headers['location'];
  }

  const codeMatch = redirectWithCodeLocation.match(/[?&]code=([-_a-zA-Z0-9]+)&?/);
  if (!codeMatch) {
    throw new Error(`No authorization code found in redirect: ${redirectWithCodeLocation}`);
  }
  const authorizationCode = codeMatch[1];

  const response = await performPost(
    oidc.token_endpoint,
    '',
    `grant_type=authorization_code&code=${authorizationCode}&redirect_uri=${encodeURIComponent(redirectUri)}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basicAuth}`,
    },
  ).expect(200);

  return {
    accessToken: response.body.access_token,
    refreshToken: response.body.refresh_token,
    idToken: response.body.id_token,
    expiresIn: response.body.expires_in,
  };
}

/**
 * Polls introspection until the token becomes inactive, with timeout.
 */
export async function waitForTokenInactive(
  introspectFn: (token: string) => Promise<Record<string, unknown>>,
  token: string,
  timeoutMs = 30000,
  intervalMs = 300,
): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await introspectFn(token);
    if (result.active === false) return;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(`Token did not become inactive within ${timeoutMs}ms`);
}
