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
import { setup } from '../../test-fixture';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { decodeJwt } from '@utils-commands/jwt';
import { delay } from '@utils-commands/misc';
import { setupTokenAuthFixture, TokenAuthFixture } from '../token-auth-methods/fixtures/token-auth-fixture';

// RFC 7662 §2.1 makes token_type_hint an optimisation, not a filter: the server must still find the
// token if the hint is absent or wrong. TokenServiceImpl implements that by trying the hinted type
// first and falling back (introspectAsRefreshTokenFirst / introspectAsAccessTokenFirst), but the
// only coverage of that dispatch is IntrospectionEndpointTest, which mocks the service entirely.
// Nothing exercised a real refresh token through the endpoint until now.

setup(200000);

const OFFLINE_VERIFICATION_WINDOW_SECONDS = 10;

let fixture: TokenAuthFixture;
let basicAuth: string;

beforeAll(async () => {
  fixture = await setupTokenAuthFixture();
  basicAuth = getBase64BasicAuth(fixture.clientId, fixture.clientSecret);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const passwordGrantTokens = async (): Promise<{ accessToken: string; refreshToken: string }> => {
  const response = await performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}`,
    { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${basicAuth}` },
  ).expect(200);

  expect(response.body.refresh_token).toEqual(expect.any(String));
  return { accessToken: response.body.access_token, refreshToken: response.body.refresh_token };
};

const introspect = async (token: string, tokenTypeHint?: string): Promise<any> => {
  const body = tokenTypeHint ? `token=${encodeURIComponent(token)}&token_type_hint=${tokenTypeHint}` : `token=${encodeURIComponent(token)}`;
  const response = await performPost(fixture.oidc.introspection_endpoint, '', body, {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: `Basic ${basicAuth}`,
  }).expect(200);
  return response.body;
};

const revoke = async (token: string, tokenTypeHint: string): Promise<void> => {
  await performPost(fixture.oidc.revocation_endpoint, '', `token=${encodeURIComponent(token)}&token_type_hint=${tokenTypeHint}`, {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: `Basic ${basicAuth}`,
  }).expect(200);
};

/** Waits out the product-default offline-verification window so the token store is consulted. */
const waitUntilTokenStoreIsConsulted = async (token: string): Promise<void> => {
  const { iat } = decodeJwt(token);
  const remainingMs = (iat + OFFLINE_VERIFICATION_WINDOW_SECONDS) * 1000 - Date.now();
  if (remainingMs > 0) {
    await delay(remainingMs + 1000);
  }
};

describe('Introspection - refresh tokens', () => {
  it('reports an active refresh token when the refresh_token hint is given', async () => {
    const { refreshToken } = await passwordGrantTokens();

    const body = await introspect(refreshToken, 'refresh_token');

    expect(body.active).toBe(true);
    expect(body.client_id).toBe(fixture.clientId);
  });

  it('finds a refresh token even when no hint is given', async () => {
    const { refreshToken } = await passwordGrantTokens();

    const body = await introspect(refreshToken);

    expect(body.active).toBe(true);
    expect(body.client_id).toBe(fixture.clientId);
  });

  it('finds an access token even when the refresh_token hint is wrong', async () => {
    const { accessToken } = await passwordGrantTokens();

    const body = await introspect(accessToken, 'refresh_token');

    expect(body.active).toBe(true);
    expect(body.client_id).toBe(fixture.clientId);
  });
});

describe('Introspection - revoked refresh tokens', () => {
  it('reports a revoked refresh token as inactive', async () => {
    const { refreshToken } = await passwordGrantTokens();
    expect((await introspect(refreshToken, 'refresh_token')).active).toBe(true);

    await revoke(refreshToken, 'refresh_token');
    await waitUntilTokenStoreIsConsulted(refreshToken);

    const body = await introspect(refreshToken, 'refresh_token');

    expect(body.active).toBe(false);
  });
});
