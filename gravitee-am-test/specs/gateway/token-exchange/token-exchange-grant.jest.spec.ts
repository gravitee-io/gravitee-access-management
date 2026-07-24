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
let noScopeFixture: TokenExchangeFixture;
let customClaimsFixture: TokenExchangeFixture;

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

  noScopeFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-no-scope',
    domainDescription: 'Token exchange no-scope (subject token no scopes)',
    clientName: 'token-exchange-no-scope-client',
    grantTypes: ['password', 'urn:ietf:params:oauth:grant-type:token-exchange'],
    scopes: [],
  });

  customClaimsFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-custom-claims',
    domainDescription: 'Token exchange with custom claims',
    clientName: 'token-exchange-custom-claims-client',
    tokenCustomClaims: [
      {
        claimName: 'am_tx_user_email',
        claimValue: "{#context.attributes['user'].email}",
        tokenType: 'ACCESS_TOKEN',
      },
    ],
  });
});

afterAll(async () => {
  if (defaultFixture) {
    await defaultFixture.cleanup();
  }
  if (restrictedFixture) {
    await restrictedFixture.cleanup();
  }
  if (noScopeFixture) {
    await noScopeFixture.cleanup();
  }
  if (customClaimsFixture) {
    await customClaimsFixture.cleanup();
  }
});

describe('Token Exchange grant', () => {
  it('should exchange subject token and keep expiration constraints', async () => {
    const { oidc, application, basicAuth, obtainSubjectToken } = defaultFixture;

    // Given: subject token has openid, profile, offline_access.
    const { accessToken: subjectAccessToken, expiresIn: subjectExpiresIn } = await obtainSubjectToken();

    // When: exchange without scope param.
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
    // Then: all client default scopes are granted when scope is omitted.
    const issuedScopes = (decoded.payload['scope'] as string)?.split(' ') ?? [];
    expect(issuedScopes).toHaveLength(2);
    expect(issuedScopes).toContain('openid');
    expect(issuedScopes).toContain('profile');
    expect(issuedScopes).not.toContain('offline_access');
    expect(exchangedToken.scope).toBeDefined();
    const responseScopeSet = new Set((exchangedToken.scope as string).split(' '));
    expect(responseScopeSet.size).toBe(2);
    expect(responseScopeSet.has('openid')).toBe(true);
    expect(responseScopeSet.has('profile')).toBe(true);
    expect(responseScopeSet.has('offline_access')).toBe(false);

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

  it('should expose full user profile in context during local token exchange', async () => {
    const { oidc, basicAuth, obtainSubjectToken, user } = customClaimsFixture;

    const { accessToken: subjectAccessToken } = await obtainSubjectToken();

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
    expect(exchangedDecoded.payload['am_tx_user_email']).toBe(user.email);
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

  it('should include granted scopes in response when client requests subset', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    // Given: subject token has openid, profile, offline_access.
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile%20offline_access');

    // When: exchange with scope=openid.
    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&scope=openid`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    // Then: response and JWT have scope openid.
    expect(exchangeResponse.body.access_token).toBeDefined();
    expect(exchangeResponse.body.scope).toBe('openid');
    const decoded = parseJwt(exchangeResponse.body.access_token);
    expect(decoded.payload['scope']).toBe('openid');
  });

  it('should issue new token with no scopes when subject token has no scopes', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = noScopeFixture;

    // Given: subject token with no scopes.
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('');

    const exchangeResponse = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(exchangeResponse.body.access_token).toBeDefined();
    expect(exchangeResponse.body.scope).toBeUndefined();
    const decoded = parseJwt(exchangeResponse.body.access_token);
    expect(decoded.payload['scope']).toBeUndefined();
  });

  it('should return 400 invalid_scope when requested scope is not a subset of subject token scopes', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    // Given: subject token has openid, profile only.
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile');

    // When: request scope=offline_access (not in subject).
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&scope=offline_access`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    // Then: 400 invalid_scope.
    expect(response.body.error).toBe('invalid_scope');
    expect(response.body.error_description).toBeDefined();
    expect(String(response.body.error_description).toLowerCase()).toMatch(/scope|disallowed|invalid/);
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
