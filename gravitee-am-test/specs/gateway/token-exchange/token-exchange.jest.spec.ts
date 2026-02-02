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
let delegationFixture: TokenExchangeFixture;
let impersonationOnlyFixture: TokenExchangeFixture;
let limitedDepthFixture: TokenExchangeFixture;
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

  // Setup delegation fixture with delegation enabled
  delegationFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-delegation',
    domainDescription: 'Token exchange with delegation',
    clientName: 'token-exchange-delegation-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth: 3,
  });

  // Setup impersonation-only fixture (delegation disabled)
  impersonationOnlyFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-impersonation',
    domainDescription: 'Token exchange impersonation only',
    clientName: 'token-exchange-impersonation-client',
    allowImpersonation: true,
    allowDelegation: false,
  });

  // Setup delegation fixture with maxDelegationDepth=1
  limitedDepthFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-depth-limit',
    domainDescription: 'Token exchange depth limit',
    clientName: 'token-exchange-depth-limit-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth: 1,
  });
});

afterAll(async () => {
  if (defaultFixture) {
    await defaultFixture.cleanup();
  }
  if (restrictedFixture) {
    await restrictedFixture.cleanup();
  }
  if (delegationFixture) {
    await delegationFixture.cleanup();
  }
  if (impersonationOnlyFixture) {
    await impersonationOnlyFixture.cleanup();
  }
  if (limitedDepthFixture) {
    await limitedDepthFixture.cleanup();
  }
  if (accessTokenOnlyFixture) {
    await accessTokenOnlyFixture.cleanup();
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

  it('should keep gis claim from subject token', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken();
    const subjectDecoded = parseJwt(subjectAccessToken);
    const subjectGis = subjectDecoded.payload['gis'];
    expect(subjectGis).toBeDefined();

    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const exchangedDecoded = parseJwt(exchangeResponse.body.access_token);
    expect(exchangedDecoded.payload['gis']).toBe(subjectGis);
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

describe('Token Exchange Delegation (RFC 8693)', () => {
  it('should exchange token with actor_token and include act claim', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken, obtainActorToken, user } = delegationFixture;

    // Obtain subject token (the user on whose behalf the action is performed)
    const { accessToken: subjectToken } = await obtainSubjectToken();

    // Obtain actor token (the entity performing the action)
    const { accessToken: actorToken } = await obtainActorToken();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();
    expect(response.body.refresh_token).toBeUndefined();

    // Verify the exchanged token contains the act claim
    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(application.settings.oauth.clientId);

    // The act claim should contain the actor's subject
    const actClaim = decoded.payload['act'] as Record<string, unknown>;
    expect(actClaim).toBeDefined();
    expect(actClaim['sub']).toBeDefined();
  });

  it('should reject delegation when delegation is not allowed', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = impersonationOnlyFixture;

    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { accessToken: actorToken } = await obtainActorToken();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description.toLowerCase()).toContain('delegation');
  });

  it('should reject when actor_token_type is not allowed', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { refreshToken: actorRefreshToken } = await obtainActorToken();
    expect(actorRefreshToken).toBeDefined();

    // refresh_token is not in the allowed actor token types
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorRefreshToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:refresh_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('actor_token_type');
  });

  it('should reject when actor_token is provided without actor_token_type', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { accessToken: actorToken } = await obtainActorToken();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorToken}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('actor_token_type');
  });

  it('should use id_token as actor_token when allowed', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { idToken: actorIdToken } = await obtainActorToken();
    expect(actorIdToken).toBeDefined();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorIdToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(application.settings.oauth.clientId);
    expect(decoded.payload['act']).toBeDefined();
  });

  it('should reject delegation when maxDelegationDepth is exceeded', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = limitedDepthFixture;

    const { accessToken: initialSubjectToken } = await obtainSubjectToken();
    const { accessToken: initialActorToken } = await obtainActorToken();

    // First delegation creates a token with an "act" claim (depth = 1)
    const firstExchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${initialSubjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${initialActorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const delegatedToken = firstExchangeResponse.body.access_token;
    expect(delegatedToken).toBeDefined();

    // Verify the first delegated token has an "act" claim
    const decoded = parseJwt(delegatedToken);
    expect(decoded.payload['act']).toBeDefined();

    // Get a new actor token (client credentials or another user token)
    const { accessToken: newActorToken } = await obtainActorToken();

    // Second delegation should fail because the subject_token already has depth 1
    // and adding another actor would make depth 2, exceeding maxDelegationDepth = 1.
    // Per RFC 8693 Section 4.1, the delegation chain is tracked via the subject token's "act" claim.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${delegatedToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${newActorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('Maximum delegation depth exceeded');
  });

  it('should calculate delegation depth from subject_token, not actor_token (RFC 8693)', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = limitedDepthFixture;

    // Create a delegated token (with act claim) to use as actor_token
    const { accessToken: subjectForActor } = await obtainSubjectToken();
    const { accessToken: actorForActor } = await obtainActorToken();

    const actorWithActResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectForActor}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorForActor}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const actorTokenWithAct = actorWithActResponse.body.access_token;
    const actorDecoded = parseJwt(actorTokenWithAct);
    expect(actorDecoded.payload['act']).toBeDefined(); // Actor token has act claim

    // Get a fresh subject token (no act claim, depth = 0)
    const { accessToken: freshSubjectToken } = await obtainSubjectToken();
    const subjectDecoded = parseJwt(freshSubjectToken);
    expect(subjectDecoded.payload['act']).toBeUndefined(); // Subject token has no act claim

    // This exchange should SUCCEED because:
    // - subject_token has no act claim (depth = 0)
    // - resulting depth would be 1, which equals maxDelegationDepth = 1
    // - the actor_token's act claim is NOT counted for depth calculation
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${freshSubjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorTokenWithAct}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // Verify the exchange succeeded and has an act claim
    const resultDecoded = parseJwt(response.body.access_token);
    expect(resultDecoded.payload['act']).toBeDefined();
    // The act claim should contain the actor's subject (from actorTokenWithAct)
    const actClaim = resultDecoded.payload['act'] as Record<string, unknown>;
    expect(actClaim['sub']).toBe(subjectDecoded.payload['sub']);
  });

  it('should allow delegation up to maxDelegationDepth and reject beyond', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture; // maxDelegationDepth = 3

    // Build a delegation chain step by step
    const { accessToken: token0 } = await obtainSubjectToken(); // depth 0, no act claim

    // First delegation: depth 0 → 1
    const { accessToken: actor1 } = await obtainActorToken();
    const response1 = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${token0}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actor1}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);
    const token1 = response1.body.access_token;
    const decoded1 = parseJwt(token1);
    expect(decoded1.payload['act']).toBeDefined();
    expect((decoded1.payload['act'] as Record<string, unknown>)['act']).toBeUndefined(); // depth 1, no nested act

    // Second delegation: depth 1 → 2
    const { accessToken: actor2 } = await obtainActorToken();
    const response2 = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${token1}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actor2}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);
    const token2 = response2.body.access_token;
    const decoded2 = parseJwt(token2);
    expect(decoded2.payload['act']).toBeDefined();
    const act2 = decoded2.payload['act'] as Record<string, unknown>;
    expect(act2['act']).toBeDefined(); // depth 2, has nested act

    // Third delegation: depth 2 → 3
    const { accessToken: actor3 } = await obtainActorToken();
    const response3 = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${token2}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actor3}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);
    const token3 = response3.body.access_token;
    const decoded3 = parseJwt(token3);
    expect(decoded3.payload['act']).toBeDefined();
    const act3 = decoded3.payload['act'] as Record<string, unknown>;
    const act3nested = act3['act'] as Record<string, unknown>;
    expect(act3nested['act']).toBeDefined(); // depth 3, has doubly nested act

    // Fourth delegation: depth 3 → 4, should FAIL (maxDelegationDepth = 3)
    const { accessToken: actor4 } = await obtainActorToken();
    const response4 = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${token3}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actor4}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response4.body.error).toBe('invalid_request');
    expect(response4.body.error_description).toContain('Maximum delegation depth exceeded');
  });

  it('should introspect delegation token and return act claim', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { accessToken: actorToken } = await obtainActorToken();

    // Exchange with delegation
    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // Introspect the delegation token
    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${exchangeResponse.body.access_token}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.act).toBeDefined();
    expect(introspectResponse.body.act.sub).toBeDefined();
  });

  it('should nest act claims correctly in chained delegation (RFC 8693 Section 4.1)', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    // Step 1: Get initial subject and actor tokens
    const { accessToken: initialSubjectToken } = await obtainSubjectToken();
    const { accessToken: actor1Token } = await obtainActorToken();

    // Step 2: First delegation - creates token with act: {sub: actor1}
    const firstExchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${initialSubjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actor1Token}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const firstDelegatedToken = firstExchangeResponse.body.access_token;
    const firstDecoded = parseJwt(firstDelegatedToken);
    const firstActClaim = firstDecoded.payload['act'] as Record<string, unknown>;
    expect(firstActClaim).toBeDefined();
    expect(firstActClaim['sub']).toBeDefined();
    const actor1Sub = firstActClaim['sub'];

    // Step 3: Get a new actor token (actor2)
    const { accessToken: actor2Token } = await obtainActorToken();
    const actor2Decoded = parseJwt(actor2Token);
    const actor2Sub = actor2Decoded.payload['sub'];

    // Step 4: Second delegation - use first delegated token as subject_token
    // Per RFC 8693 Section 4.1, the resulting token should have nested act claim:
    // act: {sub: actor2, act: {sub: actor1}}
    const secondExchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${firstDelegatedToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actor2Token}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const secondDelegatedToken = secondExchangeResponse.body.access_token;
    const secondDecoded = parseJwt(secondDelegatedToken);
    const secondActClaim = secondDecoded.payload['act'] as Record<string, unknown>;

    // Verify the nested act claim structure
    expect(secondActClaim).toBeDefined();
    expect(secondActClaim['sub']).toBe(actor2Sub);

    // The nested act claim should contain the previous delegation chain
    const nestedAct = secondActClaim['act'] as Record<string, unknown>;
    expect(nestedAct).toBeDefined();
    expect(nestedAct['sub']).toBe(actor1Sub);

    // The subject (resource owner) should be preserved from the original token
    const originalSubjectDecoded = parseJwt(initialSubjectToken);
    expect(secondDecoded.payload['sub']).toBe(originalSubjectDecoded.payload['sub']);
  });

  it('should preserve subject identity in delegation token', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken, user } = delegationFixture;

    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { accessToken: actorToken } = await obtainActorToken();

    // Get subject's identity from original token
    const subjectDecoded = parseJwt(subjectToken);
    const subjectSub = subjectDecoded.payload['sub'];
    const subjectGis = subjectDecoded.payload['gis'];

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // The exchanged token should preserve the subject's identity
    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['sub']).toBe(subjectSub);
    expect(decoded.payload['gis']).toBe(subjectGis);
  });

  it('should include actor gis in act claim for V2 domain identification', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { accessToken: actorToken } = await obtainActorToken();

    // Get actor's identity from actor token
    const actorDecoded = parseJwt(actorToken);
    const actorSub = actorDecoded.payload['sub'];
    const actorGis = actorDecoded.payload['gis'];
    expect(actorGis).toBeDefined(); // Actor token should have gis

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // The exchanged token's act claim should include actor's sub and gis
    const decoded = parseJwt(response.body.access_token);
    const actClaim = decoded.payload['act'] as Record<string, unknown>;
    expect(actClaim).toBeDefined();
    expect(actClaim['sub']).toBe(actorSub);
    expect(actClaim['gis']).toBe(actorGis);
  });

  it('should include actor_act when actor token is itself delegated', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    // Step 1: Create a delegated token to use as the actor token
    const { accessToken: firstSubjectToken } = await obtainSubjectToken();
    const { accessToken: firstActorToken } = await obtainActorToken();

    const firstActorDecoded = parseJwt(firstActorToken);
    const firstActorSub = firstActorDecoded.payload['sub'];
    const firstActorGis = firstActorDecoded.payload['gis'];

    // First delegation - creates a token with act claim
    const firstDelegationResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${firstSubjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${firstActorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const delegatedActorToken = firstDelegationResponse.body.access_token;
    const delegatedActorDecoded = parseJwt(delegatedActorToken);
    expect(delegatedActorDecoded.payload['act']).toBeDefined(); // Actor token has act claim

    // Step 2: Use the delegated token as actor_token for another delegation
    const { accessToken: secondSubjectToken } = await obtainSubjectToken();

    const secondDelegationResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${secondSubjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${delegatedActorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // Verify the resulting token has actor_act
    const finalTokenDecoded = parseJwt(secondDelegationResponse.body.access_token);
    const actClaim = finalTokenDecoded.payload['act'] as Record<string, unknown>;
    expect(actClaim).toBeDefined();

    // act.sub should be the delegated actor's sub (from first subject token)
    expect(actClaim['sub']).toBeDefined();

    // actor_act should contain the first actor's delegation chain
    const actorAct = actClaim['actor_act'] as Record<string, unknown>;
    expect(actorAct).toBeDefined();
    expect(actorAct['sub']).toBe(firstActorSub);
    expect(actorAct['gis']).toBe(firstActorGis);
  });
});
