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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { parseJwt } from '@api-fixtures/jwt';
import { setup } from '../../test-fixture';
import {
  setupTokenExchangeFixture,
  TokenExchangeFixture,
} from './fixtures/token-exchange-fixture';

setup(120000);

let defaultFixture: TokenExchangeFixture;
let restrictedFixture: TokenExchangeFixture;
let accessTokenOnlyFixture: TokenExchangeFixture;

beforeAll(async () => {
  // Setup default fixture with all token types allowed (impersonation only by default)
  defaultFixture = await setupTokenExchangeFixture();

  // Setup restricted fixture with only ID tokens allowed as subject
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

  // Setup fixture with only access_token allowed as requested type (ID token not allowed)
  accessTokenOnlyFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-access-only',
    domainDescription: 'Token exchange access token only',
    clientName: 'token-exchange-access-only-client',
    grantTypes: ['password', 'urn:ietf:params:oauth:grant-type:token-exchange'],
    scopes: [
      { scope: 'openid', defaultScope: true },
      { scope: 'profile', defaultScope: true },
    ],
    allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
  });
});

afterAll(async () => {
  if (defaultFixture) {
    await defaultFixture.cleanup();
  }
  if (restrictedFixture) {
    await restrictedFixture.cleanup();
  }
  if (accessTokenOnlyFixture) {
    await accessTokenOnlyFixture.cleanup();
  }
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

describe('Token Exchange with requested_token_type=id_token', () => {
  it('should return ID token when requested_token_type is id_token', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&requested_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // Per RFC 8693, when ID token is requested:
    // - issued_token_type should be urn:ietf:params:oauth:token-type:id_token
    // - token_type should be "N_A" (not applicable)
    // - access_token field contains the ID token
    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:id_token');
    expect(response.body.token_type).toBe('N_A');
    expect(response.body.access_token).toBeDefined();
    expect(response.body.refresh_token).toBeUndefined();

    // Verify it's a valid JWT and contains expected ID token claims
    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['sub']).toBeDefined();
    expect(decoded.payload['iss']).toBeDefined();
    expect(decoded.payload['aud']).toBeDefined();
    expect(decoded.payload['exp']).toBeDefined();
    expect(decoded.payload['iat']).toBeDefined();
  });

  it('should return ID token with expires_in consistent with subject token expiration', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    // Decode the subject token to get its expiration time
    const subjectPayload = JSON.parse(Buffer.from(subjectAccessToken.split('.')[1], 'base64').toString());
    const subjectExp = subjectPayload.exp;
    const nowSeconds = Math.floor(Date.now() / 1000);
    const subjectRemainingTime = subjectExp - nowSeconds;

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&requested_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // expires_in should be present and reasonable
    expect(response.body.expires_in).toBeDefined();
    expect(response.body.expires_in).toBeGreaterThan(0);

    // The exchanged token's expires_in should not exceed the subject token's remaining time
    // Allow a small buffer (5 seconds) for processing time
    expect(response.body.expires_in).toBeLessThanOrEqual(subjectRemainingTime + 5);
  });

  it('should NOT include scope in ID token response', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    // Request with openid scope - but ID token response should not include scope
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&requested_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // ID tokens are for identity/authentication, not authorization
    // Therefore scope should NOT be included in the response
    expect(response.body.scope).toBeUndefined();
    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:id_token');
  });

  it('should reject id_token request when not in allowedRequestedTokenTypes', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = accessTokenOnlyFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&requested_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('requested_token_type');
  });

  it('should use access_token as default when not specified', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    // Request without requested_token_type should default to access_token
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // Should default to access_token
    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.token_type.toLowerCase()).toBe('bearer');
  });
});
