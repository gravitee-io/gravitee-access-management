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

const SCOPE = 'openid email profile';
const LIGHTWEIGHT_ID_TOKEN_CLAIMS = ['aud', 'auth_time', 'exp', 'gis', 'iat', 'iss', 'sub'];

let fixture: TokenIdentityFixture;
let jwks: ReturnType<typeof createRemoteJWKSet>;

function claimNames(token: string): string[] {
  return Object.keys(decodeToken(token).payload).sort();
}

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    lightweightJwtSettings: { enabled: true },
    // the access token claim is the probe setLightweightJwt polls
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'tenant', claimValue: 'acme' },
      { tokenType: 'ID_TOKEN', claimName: 'tenant', claimValue: 'acme' },
    ],
  });
  jwks = createRemoteJWKSet(new URL(fixture.oidc.jwks_uri));
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Lightweight JWT - ID token', () => {
  it('should only keep the reserved claims in the ID token', async () => {
    const tokens = await fixture.passwordGrant(SCOPE);

    expect(claimNames(tokens.id_token)).toEqual(LIGHTWEIGHT_ID_TOKEN_CLAIMS);
  });

  it('should keep the nonce the client sent in the ID token', async () => {
    const nonce = `n-${Date.now()}`;

    const tokens = await fixture.authorizationCodeFlow(`scope=openid%20email%20profile&nonce=${nonce}`);

    expect(decodeToken(tokens.id_token).payload.nonce).toEqual(nonce);
    expect(claimNames(tokens.id_token)).toEqual([...LIGHTWEIGHT_ID_TOKEN_CLAIMS, 'nonce'].sort());
  });

  it('should issue a lightweight ID token on the refresh token grant', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(claimNames(refreshed.id_token)).toEqual(LIGHTWEIGHT_ID_TOKEN_CLAIMS);
  });

  it('should issue a lightweight ID token that a relying party can verify', async () => {
    const tokens = await fixture.passwordGrant(SCOPE);

    const { payload } = await jwtVerify(tokens.id_token, jwks, { issuer: fixture.oidc.issuer, audience: fixture.clientId });

    expect(payload.sub).toEqual(decodeToken(tokens.access_token).payload.sub);
  });
});

describe('Lightweight JWT - ID token toggle', () => {
  it('should restore the profile and custom claims in the ID token when disabled, and remove them when enabled again', async () => {
    await fixture.setLightweightJwt(false, 'tenant');
    const full = await fixture.passwordGrant(SCOPE);
    expect(decodeToken(full.id_token).payload).toMatchObject({ email: fixture.user.email, tenant: 'acme' });

    await fixture.setLightweightJwt(true, 'tenant');
    const lightweight = await fixture.passwordGrant(SCOPE);
    expect(claimNames(lightweight.id_token)).toEqual(LIGHTWEIGHT_ID_TOKEN_CLAIMS);
  });
});
