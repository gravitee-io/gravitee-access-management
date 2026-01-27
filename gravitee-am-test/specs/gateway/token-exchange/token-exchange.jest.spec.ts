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

import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { parseJwt } from '@api-fixtures/jwt';
import {
  setupTokenExchangeFixture,
  TokenExchangeFixture,
  TOKEN_EXCHANGE_TEST,
} from './fixtures/token-exchange-fixture';

globalThis.fetch = fetch;
jest.setTimeout(200000);

let defaultFixture: TokenExchangeFixture;
let restrictedFixture: TokenExchangeFixture;

beforeAll(async () => {
  // Setup default fixture with all token types allowed
  defaultFixture = await setupTokenExchangeFixture();

  // Setup restricted fixture with only ID tokens allowed
  restrictedFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-id-only',
    domainDescription: 'Token exchange ID only',
    clientName: 'token-exchange-id-only-client',
    grantTypes: ['password', 'urn:ietf:params:oauth:grant-type:token-exchange'],
    scopes: [
      { scope: 'openid', defaultScope: true },
      { scope: 'profile', defaultScope: true },
    ],
    allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:id_token'],
  });
});

afterAll(async () => {
  if (defaultFixture) {
    await defaultFixture.cleanup();
  }
  if (restrictedFixture) {
    await restrictedFixture.cleanup();
  }
});

describe('Token Exchange grant', () => {
  it('should exchange subject token and keep expiration constraints', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken, expiresIn: subjectExpiresIn } = await obtainSubjectToken();

    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const exchangedToken = exchangeResponse.body;
    expect(exchangedToken.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(exchangedToken.refresh_token).toBeUndefined();
    expect(exchangedToken.expires_in).toBeLessThanOrEqual(subjectExpiresIn);
    expect(exchangedToken.token_type.toLowerCase()).toBe('bearer');

    const decoded = parseJwt(exchangedToken.access_token);
    expect(decoded.payload['client_id']).toBe(application.settings.oauth.clientId);

    // Introspect the exchanged token to verify it's valid
    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${exchangedToken.access_token}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.client_id).toBe(application.settings.oauth.clientId);
    expect(introspectResponse.body.token_type).toBe('bearer');
  });

  it('should exchange refresh token when allowed', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken } = defaultFixture;

    const { refreshToken } = await obtainSubjectToken();
    expect(refreshToken).toBeDefined();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${refreshToken}&subject_token_type=urn:ietf:params:oauth:token-type:refresh_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();
    expect(response.body.refresh_token).toBeUndefined();

    // Introspect the exchanged token to verify it's valid
    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.client_id).toBe(application.settings.oauth.clientId);
    expect(introspectResponse.body.token_type).toBe('bearer');
  });

  it('should exchange id token when allowed', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken } = defaultFixture;

    const { idToken } = await obtainSubjectToken();
    expect(idToken).toBeDefined();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${idToken}&subject_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();
    expect(response.body.refresh_token).toBeUndefined();

    // Introspect the exchanged token to verify it's valid
    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.client_id).toBe(application.settings.oauth.clientId);
    expect(introspectResponse.body.token_type).toBe('bearer');
  });

  it('should reject when requested_token_type is not supported', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&requested_token_type=urn:ietf:params:oauth:token-type:refresh_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('requested_token_type');
  });

  it('should NOT include id_token in response when exchanging access token with openid scope', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    // Obtain a subject token with openid scope
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    // Exchange the token without specifying requested_token_type (defaults to access_token)
    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // Per RFC 8693, token exchange response should NOT include id_token unless explicitly requested
    expect(exchangeResponse.body.access_token).toBeDefined();
    expect(exchangeResponse.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(exchangeResponse.body.id_token).toBeUndefined();
  });

  it('should exchange jwt subject token when allowed', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken } = defaultFixture;
    const { accessToken: subjectAccessToken } = await obtainSubjectToken();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(application.settings.oauth.clientId);
  });

  it('should reject when subject_token_type is not allowed', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = restrictedFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:saml1`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('subject_token_type');
  });
});

describe('Token Exchange with restricted subject token types', () => {
  it('should reject access token when domain only allows id tokens', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = restrictedFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('subject_token_type');
  });

  it('should accept id token when domain only allows id tokens', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken } = restrictedFixture;

    const { idToken } = await obtainSubjectToken('openid%20profile');
    expect(idToken).toBeDefined();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${idToken}&subject_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();

    // Verify the exchanged token
    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(application.settings.oauth.clientId);
  });
});
