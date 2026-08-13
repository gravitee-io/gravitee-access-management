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
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * These tests stand in for a relying party. Rather than asserting that individual
 * claims exist, they do what a real OIDC client library does - fetch the domain
 * JWKS, verify the signature, and enforce issuer, audience and expiry. A failure
 * here means tokens AM issued have stopped being consumable, which is the shape
 * of a total-outage regression.
 */
let fixture: TokenIdentityFixture;
let jwks: ReturnType<typeof createRemoteJWKSet>;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture();
  jwks = createRemoteJWKSet(new URL(fixture.oidc.jwks_uri));
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Relying party validation - ID token', () => {
  it('verifies against the published JWKS with the expected issuer and audience', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    const { payload, protectedHeader } = await jwtVerify(tokens.id_token, jwks, {
      issuer: fixture.oidc.issuer,
      audience: fixture.clientId,
    });

    expect(protectedHeader.alg).toEqual('RS256');
    expect(protectedHeader.kid).toEqual(expect.any(String));
    expect(payload.sub).toEqual(expect.any(String));
    expect(payload.exp).toBeGreaterThan(payload.iat as number);
  });

  it('is rejected by a relying party that expects a different audience', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    await expect(jwtVerify(tokens.id_token, jwks, { issuer: fixture.oidc.issuer, audience: 'some-other-client' })).rejects.toThrow(
      /unexpected "aud" claim value/,
    );
  });

  it('echoes back the nonce the relying party sent, so it can detect replay', async () => {
    const nonce = `n-${Date.now()}`;
    const tokens = await fixture.authorizationCodeFlow(`scope=openid&nonce=${nonce}`);

    const { payload } = await jwtVerify(tokens.id_token, jwks, {
      issuer: fixture.oidc.issuer,
      audience: fixture.clientId,
    });

    expect(payload.nonce).toEqual(nonce);
  });

  it('omits nonce entirely when the relying party did not send one', async () => {
    const tokens = await fixture.passwordGrant('openid');
    expect(decodeToken(tokens.id_token).payload).not.toHaveProperty('nonce');
  });

  it('reports when the user authenticated so the relying party can apply max_age', async () => {
    const before = Math.floor(Date.now() / 1000) - 5;
    const tokens = await fixture.passwordGrant('openid email profile');
    const after = Math.floor(Date.now() / 1000) + 5;

    const authTime = decodeToken(tokens.id_token).payload.auth_time;

    expect(authTime).toBeGreaterThanOrEqual(before);
    expect(authTime).toBeLessThanOrEqual(after);
  });
});

describe('Relying party validation - access token', () => {
  it('verifies against the published JWKS with the expected issuer and audience', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    const { payload, protectedHeader } = await jwtVerify(tokens.access_token, jwks, {
      issuer: fixture.oidc.issuer,
      audience: fixture.clientId,
    });

    expect(protectedHeader.alg).toEqual('RS256');
    expect(payload.jti).toEqual(expect.any(String));
    expect(payload.client_id).toEqual(fixture.clientId);
  });

  it('still verifies after the session refreshes its token', async () => {
    const initial = await fixture.passwordGrant('openid email profile');
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    const { payload } = await jwtVerify(refreshed.access_token, jwks, {
      issuer: fixture.oidc.issuer,
      audience: fixture.clientId,
    });

    expect(payload.sub).toEqual(decodeToken(initial.access_token).payload.sub);
  });

  it('carries the granted scopes so a resource server can authorise the call', async () => {
    const tokens = await fixture.passwordGrant('openid email');

    const { payload } = await jwtVerify(tokens.access_token, jwks, {
      issuer: fixture.oidc.issuer,
      audience: fixture.clientId,
    });

    const granted = (payload.scope as string).split(' ');
    expect(granted).toEqual(expect.arrayContaining(['openid', 'email']));
    expect(tokens.scope.split(' ').sort()).toEqual(granted.sort());
  });

  it('gives every issued token a distinct identifier', async () => {
    const first = await fixture.passwordGrant('openid');
    const second = await fixture.passwordGrant('openid');

    const firstJti = decodeToken(first.access_token).payload.jti;
    const secondJti = decodeToken(second.access_token).payload.jti;

    expect(firstJti).toEqual(expect.any(String));
    expect(secondJti).not.toEqual(firstJti);
  });
});
