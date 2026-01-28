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
import { assertGeneratedToken, OAuth2Fixture, setupFixture } from './fixture/oauth2-fixture';
import {
  extractXsrfTokenAndActionResponse,
  logoutUser,
  performFormPost,
  performGet,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { patchApplication } from '@management-commands/application-management-commands';
import { patchDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { getParamsInvalidAuthorizeRequests } from './fixture/oauth2-invalid-params-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: OAuth2Fixture;
let user: {
  username: string;
  password: string;
};

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

describe('OAuth2 - RFC 6746 - Authorization Code Grant', () => {
  it('must handle unknown scope', async () => {
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&scope=unknown-scope`;
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
    expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

    const { headers, token, action } = await extractXsrfTokenAndActionResponse(authResponse);
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

    const errorRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(errorRedirect.headers['location']).toBeDefined();
    expect(errorRedirect.headers['location']).toContain(`error=invalid_scope`);
    expect(errorRedirect.headers['location']).toContain(`error_description=Invalid+scope%28s%29%3A+unknown-scope`);
  });

  it('must handle consent with default scope', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
    expect(loginLocation).toContain(`client_id=${clientId}`);

    // Redirect to /login
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
    // Authentication
    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: user.password,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Post authentication
    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(postLoginRedirect.headers['location']).toBeDefined();
    expect(postLoginRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/oauth/consent`);

    // Redirect to /oauth/consent
    const consentResult = await extractXsrfTokenAndActionResponse(postLoginRedirect);

    // Post consent
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

    // Redirect to URI
    const postConsentRedirect = await performGet(postConsent.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(postConsentRedirect.headers['location']).toBeDefined();
    expect(postConsentRedirect.headers['location']).toContain('http://localhost:4000/?');
    expect(postConsentRedirect.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+&?/);

    await logoutUser(fixture.oidc.end_session_endpoint, postConsentRedirect);
  });

  it('must handle code flow with fragment response_mode', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=code&response_mode=fragment&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
    expect(loginLocation).toContain(`client_id=${clientId}`);

    // Redirect to /login
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
    // Authentication
    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: user.password,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Redirect to URI
    const postConsentRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(postConsentRedirect.headers['location']).toBeDefined();
    // as response_mode = fragment, the code is provided as fragment (#) instead of query param (?)
    expect(postConsentRedirect.headers['location']).toContain('http://localhost:4000/#');
    expect(postConsentRedirect.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+&?/);

    await logoutUser(fixture.oidc.end_session_endpoint, postConsentRedirect);
  });

  it('must handle consent with scope expiry', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=code&client_id=${clientId}&scope=${fixture.shortLivingScope.key}&redirect_uri=http://localhost:4000/`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
    expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

    // Redirect to /login
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

    // Authentication
    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: user.password,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Post authentication redirect
    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(postLoginRedirect.headers['location']).toBeDefined();
    expect(postLoginRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/oauth/consent`);

    // Redirect to /oauth/consent
    const consentResult = await extractXsrfTokenAndActionResponse(postLoginRedirect);

    // Post consent
    const postConsent = await performFormPost(
      consentResult.action,
      '',
      {
        'X-XSRF-TOKEN': consentResult.token,
        'scope.openid': true,
        'scope.short': true,
        user_oauth_approval: true,
      },
      {
        Cookie: consentResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Redirect to URI
    const postConsentRedirect = await performGet(postConsent.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(postConsentRedirect.headers['location']).toBeDefined();
    expect(postConsentRedirect.headers['location']).toContain('http://localhost:4000/?');
    expect(postConsentRedirect.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+&?/);

    // Initiate the Login Flow again
    const authResponse2 = await waitForDomainSync(fixture.masterDomain.id, fixture.accessToken).then((_) =>
      performGet(fixture.oidc.authorization_endpoint, params, {
        Cookie: postConsentRedirect.headers['set-cookie'],
      }).expect(302),
    );

    expect(authResponse2.headers['location']).toBeDefined();
    expect(authResponse2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/oauth/consent`);

    await logoutUser(fixture.oidc.end_session_endpoint, postConsentRedirect)
      .then((_) =>
        patchApplication(fixture.masterDomain.id, fixture.accessToken, { settings: fixture.application.settings }, fixture.application.id),
      )
      .then((_) => waitForDomainSync(fixture.masterDomain.id, fixture.accessToken));
  });

  it('must handle invalid client', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
    expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

    // Redirect to /login
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

    // Authentication
    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: user.password,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Post authentication redirect
    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
    expect(postLoginRedirect.headers['location']).toBeDefined();
    expect(postLoginRedirect.headers['location']).toContain('http://localhost:4000/?');
    expect(postLoginRedirect.headers['location']).toMatch(codePattern);

    const code = postLoginRedirect.headers['location'].match(codePattern)[1];
    const badGrantResponse = await performPost(
      `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${code}&redirect_uri=http://localhost:4000/`,
      '',
      null,
      {
        Authorization: `Basic ${applicationBase64Token(fixture.anotherApplication)}`,
      },
    ).expect(400);

    expect(badGrantResponse.body.error).toEqual('invalid_grant');
    expect(badGrantResponse.body.error_description).toEqual(`The authorization code ${code} is invalid.`);

    await logoutUser(fixture.oidc.end_session_endpoint, postLoginRedirect);
  });

  it('must handle invalid redirect_uri', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
    expect(loginLocation).toContain(`client_id=${clientId}`);

    // Redirect to /login
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

    // Authentication
    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: user.password,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Post authentication redirect
    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    const redirectUri = postLoginRedirect.headers['location'];
    const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
    expect(redirectUri).toBeDefined();
    expect(redirectUri).toContain('http://localhost:4000/?');
    expect(redirectUri).toMatch(codePattern);

    const authorizationCode = redirectUri.match(codePattern)[1];
    const badGrantResponse = await performPost(
      `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=http://localhost:5000/`,
      '',
      null,
      {
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    ).expect(400);

    expect(badGrantResponse.body.error).toEqual('invalid_grant');
    expect(badGrantResponse.body.error_description).toEqual('Redirect URI mismatch.');

    await logoutUser(fixture.oidc.end_session_endpoint, postLoginRedirect);
  });

  it('must handle with state parameter', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
    expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

    // Redirect to /login
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

    // Authentication
    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: user.password,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    // Post authentication redirect
    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    const redirectUri = postLoginRedirect.headers['location'];
    const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
    expect(redirectUri).toBeDefined();
    expect(redirectUri).toContain('http://localhost:4000/?');
    expect(redirectUri).toContain('state=');
    expect(redirectUri).toMatch(codePattern);

    const authorizationCode = redirectUri.match(codePattern)[1];

    const tokenResult = await performPost(
      `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=http://localhost:4000/`,
      '',
      null,
      {
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    ).expect(200);

    expect(tokenResult.body.access_token).toBeDefined();
    expect(tokenResult.body.token_type).toBeDefined();
    expect(tokenResult.body.token_type).toEqual('bearer');
    expect(tokenResult.body.expires_in).toBeDefined();
    expect(tokenResult.body.scope).toBeDefined();
    expect(tokenResult.body.scope).toEqual('scope1');

    await logoutUser(fixture.oidc.end_session_endpoint, postLoginRedirect);
  });

  for (const codeChallengeMethod of ['S256', 'plain']) {
    it('must miss code_verifier PKCE - ' + codeChallengeMethod, async () => {
      const clientId = fixture.application.settings.oauth.clientId;
      const params =
        `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876` +
        `&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=${codeChallengeMethod}`;

      // Initiate the Login Flow
      const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

      expect(authResponse.headers['location']).toBeDefined();
      expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
      expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

      const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

      // Authentication
      const postLogin = await performFormPost(
        loginResult.action,
        '',
        {
          'X-XSRF-TOKEN': loginResult.token,
          username: user.username,
          password: user.password,
          client_id: clientId,
        },
        {
          Cookie: loginResult.headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);

      // Post authentication redirect
      const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
        Cookie: postLogin.headers['set-cookie'],
      }).expect(302);

      const redirectUri = postLoginRedirect.headers['location'];
      const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
      expect(redirectUri).toBeDefined();
      expect(redirectUri).toContain('http://localhost:4000/?');
      expect(redirectUri).toContain('state=');
      expect(redirectUri).toMatch(codePattern);

      const authorizationCode = redirectUri.match(codePattern)[1];

      const badGrantResponse = await performPost(
        `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=http://localhost:4000/`,
        '',
        null,
        {
          Authorization: 'Basic ' + applicationBase64Token(fixture.application),
        },
      ).expect(400);

      expect(badGrantResponse.body.error).toEqual('invalid_grant');
      expect(badGrantResponse.body.error_description).toEqual('Missing parameter: code_verifier');

      await logoutUser(fixture.oidc.end_session_endpoint, postLoginRedirect);
    });

    it('must have invalid code_verifier PKCE - ' + codeChallengeMethod, async () => {
      const clientId = fixture.application.settings.oauth.clientId;
      const params =
        `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876` +
        `&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=${codeChallengeMethod}`;

      // Initiate the Login Flow
      const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

      expect(authResponse.headers['location']).toBeDefined();
      expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
      expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

      const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

      // Authentication
      const postLogin = await performFormPost(
        loginResult.action,
        '',
        {
          'X-XSRF-TOKEN': loginResult.token,
          username: user.username,
          password: user.password,
          client_id: clientId,
        },
        {
          Cookie: loginResult.headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);

      // Post authentication redirect
      const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
        Cookie: postLogin.headers['set-cookie'],
      }).expect(302);

      const redirectUri = postLoginRedirect.headers['location'];
      const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
      expect(redirectUri).toBeDefined();
      expect(redirectUri).toContain('http://localhost:4000/?');
      expect(redirectUri).toContain('state=');
      expect(redirectUri).toMatch(codePattern);

      const authorizationCode = redirectUri.match(codePattern)[1];

      const badGrantResponse = await performPost(
        `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${authorizationCode}` +
          `&code_verifier=wrongcodeverifier&redirect_uri=http://localhost:4000/`,
        '',
        null,
        {
          Authorization: 'Basic ' + applicationBase64Token(fixture.application),
        },
      ).expect(400);

      expect(badGrantResponse.body.error).toEqual('invalid_grant');
      expect(badGrantResponse.body.error_description).toEqual('Invalid parameter: code_verifier');

      await logoutUser(fixture.oidc.end_session_endpoint, postLoginRedirect);
    });
  }

  for (const challenge of [
    { method: 'S256', code_verifier: 'M25iVXpKU3puUjFaYWg3T1NDTDQtcW1ROUY5YXlwalNoc0hhakxifmZHag' },
    { method: 'plain', code_verifier: 'qjrzSW9gMiUgpUvqgEPE4_-8swvyCtfOVvg55o5S_es' },
  ]) {
    it('must have valid code_verifier PKCE - ' + challenge.method, async () => {
      const clientId = fixture.application.settings.oauth.clientId;
      const params =
        `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&state=1234-5678-9876` +
        `&code_challenge=qjrzSW9gMiUgpUvqgEPE4_-8swvyCtfOVvg55o5S_es&code_challenge_method=${challenge.method}`;

      // Initiate the Login Flow
      const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

      expect(authResponse.headers['location']).toBeDefined();
      expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/login`);
      expect(authResponse.headers['location']).toContain(`client_id=${clientId}`);

      const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

      // Authentication
      const postLogin = await performFormPost(
        loginResult.action,
        '',
        {
          'X-XSRF-TOKEN': loginResult.token,
          username: user.username,
          password: user.password,
          client_id: clientId,
        },
        {
          Cookie: loginResult.headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);

      // Post authentication redirect
      const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
        Cookie: postLogin.headers['set-cookie'],
      }).expect(302);

      const redirectUri = postLoginRedirect.headers['location'];
      const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
      expect(redirectUri).toBeDefined();
      expect(redirectUri).toContain('http://localhost:4000/?');
      expect(redirectUri).toContain('state=');
      expect(redirectUri).toMatch(codePattern);

      const authorizationCode = redirectUri.match(codePattern)[1];

      const response = await performPost(
        fixture.oidc.token_endpoint,
        `?grant_type=authorization_code&code=${authorizationCode}&code_verifier=${challenge.code_verifier}&redirect_uri=http://localhost:4000/`,
        null,
        {
          Authorization: 'Basic ' + applicationBase64Token(fixture.application),
        },
      ).expect(200);

      assertGeneratedToken(response.body, 'scope1');
      await logoutUser(fixture.oidc.end_session_endpoint, postLoginRedirect);
    });
  }

  describe('Authorize Request', () => {
    // Generate test cases - we'll use a placeholder that gets replaced at test time
    const testCases = getParamsInvalidAuthorizeRequests('__CLIENT_ID_PLACEHOLDER__');

    for (const actual of testCases) {
      it('must handle invalid Authorize requests - ' + actual.title, async () => {
        // Get the actual client ID at test execution time
        const clientId = fixture.application.settings.oauth.clientId;
        const params = actual.params.replace(/__CLIENT_ID_PLACEHOLDER__/g, clientId);

        // Initiate the Login Flow
        let authResponse;
        if (actual.patchDomain) {
          authResponse = await patchDomain(fixture.masterDomain.id, fixture.accessToken, {
            oidc: {
              redirectUriStrictMatching: true,
            },
          })
            .then((_) => waitForDomainSync(fixture.masterDomain.id, fixture.accessToken))
            .then((_) => performGet(fixture.oidc.authorization_endpoint, params).expect(302));
        } else {
          authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
        }

        expect(authResponse.header['location']).toBeDefined();
        expect(authResponse.header['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}${actual.uri}`);

        if (actual.error && actual.error_description) {
          const error = authResponse.header['location'].match(/error=([^&]+)&?/)[1];
          const errorDescription = authResponse.header['location'].match(/error_description=([^&]+)&?/)[1];
          expect(error).toEqual(actual.error);
          if (error !== 'redirect_uri_mismatch') {
            expect(errorDescription).toEqual(actual.error_description);
          }
        }
        if (actual.state) {
          const state = authResponse.header['location'].match(/state=([^&]+)&?/)[1];
          expect(state).toEqual(actual.state);
        }
      });
    }
  });
});
