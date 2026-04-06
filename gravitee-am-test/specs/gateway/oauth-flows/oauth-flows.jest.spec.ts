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
  logoutUser,
  performFormPost,
  performGet,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { OAuth2Fixture, setupFixture, assertGeneratedToken } from '../oauth2/fixture/oauth2-fixture';
import crypto from 'crypto';

setup(200000);

/**
 * Helper: execute the full auth code flow (login + consent if needed) and return the authorization code.
 * Reuses the redirect chain pattern from oauth2-grant-authorization-code.jest.spec.ts.
 */
async function executeAuthCodeFlow(
  fixture: OAuth2Fixture,
  user: { username: string; password: string },
  extraParams = '',
): Promise<{ code: string; cookies: string[]; lastRedirect: any }> {
  const clientId = fixture.application.settings.oauth.clientId;
  const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/${extraParams ? '&' + extraParams.replace(/^&/, '') : ''}`;

  // GET /authorize → 302 to /login
  const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
  const { headers, token, action } = await extractXsrfTokenAndActionResponse(authResponse);

  // POST /login
  const postLogin = await performFormPost(
    action,
    '',
    {
      'X-XSRF-TOKEN': token,
      username: user.username,
      password: user.password,
      client_id: clientId,
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  // Follow redirect — may go to consent or directly to callback
  let redirectResponse = await performGet(postLogin.headers['location'], '', {
    Cookie: postLogin.headers['set-cookie'],
  }).expect(302);

  // If consent page, accept it
  if (redirectResponse.headers['location']?.includes('/oauth/consent')) {
    const consentResult = await extractXsrfTokenAndActionResponse(redirectResponse);
    const postConsent = await performFormPost(
      consentResult.action,
      '',
      {
        'X-XSRF-TOKEN': consentResult.token,
        'scope.scope1': true,
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
  const code = redirectResponse.headers['location'].match(codePattern)[1];

  return { code, cookies: redirectResponse.headers['set-cookie'], lastRedirect: redirectResponse };
}

describe('OAuth Grant Flows — Regression Suite', () => {
  describe('Authorization Code Flow', () => {
    let fixture: OAuth2Fixture;
    let user: { username: string; password: string };

    beforeAll(async () => {
      fixture = await setupFixture({
        withOpenidScope: false,
        type: 'WEB',
        grantTypes: ['authorization_code'],
      });
      user = fixture.users[0];
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should complete auth code flow and obtain access token ${'AM-2205'}`, async () => {
      const { code, lastRedirect } = await executeAuthCodeFlow(fixture, user);

      // Exchange code for token
      const tokenResponse = await performPost(
        `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${code}&redirect_uri=http://localhost:4000/`,
        '',
        null,
        {
          Authorization: 'Basic ' + applicationBase64Token(fixture.application),
        },
      ).expect(200);

      assertGeneratedToken(tokenResponse.body, 'scope1');
      await logoutUser(fixture.oidc.end_session_endpoint, lastRedirect);
    });
  });

  describe('Authorization Code + PKCE Flow', () => {
    let fixture: OAuth2Fixture;
    let user: { username: string; password: string };

    beforeAll(async () => {
      fixture = await setupFixture({
        withOpenidScope: false,
        type: 'WEB',
        grantTypes: ['authorization_code'],
      });
      user = fixture.users[0];
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should complete auth code + PKCE (S256) flow ${'AM-2238'}`, async () => {
      // Generate PKCE code_verifier and code_challenge
      const codeVerifier = crypto.randomBytes(32).toString('base64url');
      const codeChallenge = crypto.createHash('sha256').update(codeVerifier).digest('base64url');

      const { code, lastRedirect } = await executeAuthCodeFlow(
        fixture,
        user,
        `code_challenge=${codeChallenge}&code_challenge_method=S256`,
      );

      // Exchange code for token with code_verifier
      const tokenResponse = await performPost(
        `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${code}&code_verifier=${codeVerifier}&redirect_uri=http://localhost:4000/`,
        '',
        null,
        {
          Authorization: 'Basic ' + applicationBase64Token(fixture.application),
        },
      ).expect(200);

      assertGeneratedToken(tokenResponse.body, 'scope1');
      await logoutUser(fixture.oidc.end_session_endpoint, lastRedirect);
    });
  });

  describe('Implicit Flow', () => {
    let fixture: OAuth2Fixture;
    let user: { username: string; password: string };

    beforeAll(async () => {
      fixture = await setupFixture({
        withOpenidScope: false,
        type: 'WEB',
        grantTypes: ['implicit'],
      });
      user = fixture.users[0];
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should obtain access token via implicit flow ${'AM-2231'}`, async () => {
      const clientId = fixture.application.settings.oauth.clientId;
      const params = `?response_type=token&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

      // GET /authorize → 302 to /login
      const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
      const { headers, token, action } = await extractXsrfTokenAndActionResponse(authResponse);

      // POST /login
      const postLogin = await performFormPost(
        action,
        '',
        {
          'X-XSRF-TOKEN': token,
          username: user.username,
          password: user.password,
          client_id: clientId,
        },
        {
          Cookie: headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);

      // Follow redirect chain
      let redirectResponse = await performGet(postLogin.headers['location'], '', {
        Cookie: postLogin.headers['set-cookie'],
      }).expect(302);

      // Handle consent if present
      if (redirectResponse.headers['location']?.includes('/oauth/consent')) {
        const consentResult = await extractXsrfTokenAndActionResponse(redirectResponse);
        const postConsent = await performFormPost(
          consentResult.action,
          '',
          {
            'X-XSRF-TOKEN': consentResult.token,
            'scope.scope1': true,
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

      // Implicit flow returns token in URL fragment (#)
      const location = redirectResponse.headers['location'];
      expect(location).toContain('http://localhost:4000/#');
      expect(location).toMatch(/access_token=[-_a-zA-Z0-9.]+/);
      expect(location).toContain('token_type=bearer');
    });
  });

  describe('Resource Owner Password', () => {
    let fixture: OAuth2Fixture;
    let user: { username: string; password: string };

    beforeAll(async () => {
      fixture = await setupFixture({
        withOpenidScope: false,
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

    it(jira`should obtain token via resource owner password grant ${'AM-2229'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(user.username)}&password=${encodeURIComponent(user.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + applicationBase64Token(fixture.application),
        },
      ).expect(200);

      assertGeneratedToken(response.body, 'scope1');
    });
  });

  describe('Client Credentials', () => {
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

    it(jira`should obtain token via client credentials ${'AM-2213'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        'grant_type=client_credentials',
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + applicationBase64Token(fixture.application),
        },
      ).expect(200);

      assertGeneratedToken(response.body, 'scope1');
      // Client credentials has no user context — no refresh token
      expect(response.body.refresh_token).toBeUndefined();
    });
  });
});
