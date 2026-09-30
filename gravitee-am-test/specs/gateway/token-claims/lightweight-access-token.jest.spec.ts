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
import { decodeToken, LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

const SCOPE = 'openid email profile';

let fixture: TokenIdentityFixture;

function claimNames(token: string): string[] {
  return Object.keys(decodeToken(token).payload).sort();
}

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    lightweightJwtSettings: { enabled: true },
    tokenCustomClaims: [{ tokenType: 'ACCESS_TOKEN', claimName: 'tenant', claimValue: 'acme' }],
    userinfoCustomClaims: [{ claimName: 'tenant', claimValue: 'acme' }],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Lightweight JWT - access token', () => {
  it('should only keep the reserved claims in the access token', async () => {
    const tokens = await fixture.passwordGrant(SCOPE);

    expect(claimNames(tokens.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
  });

  it('should keep the reserved claims values in the access token', async () => {
    const tokens = await fixture.passwordGrant(SCOPE);
    const payload = decodeToken(tokens.access_token).payload;

    expect(payload.client_id).toEqual(fixture.clientId);
    expect(payload.aud).toEqual(fixture.clientId);
    expect(payload.scope.split(' ').sort()).toEqual(['email', 'openid', 'profile']);
  });

  it('should issue a lightweight access token on the refresh token grant', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(claimNames(refreshed.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
  });
});

describe('Lightweight JWT - other tokens and endpoints are unchanged', () => {
  it('should keep the custom claim in the refresh token', async () => {
    const tokens = await fixture.passwordGrant(SCOPE);

    expect(decodeToken(tokens.refresh_token).payload.tenant).toEqual('acme');
  });

  it('should answer UserInfo with the profile and custom claims for a lightweight access token', async () => {
    const tokens = await fixture.passwordGrant(SCOPE);

    const userinfo = await fixture.userinfo(tokens.access_token);

    expect(userinfo).toMatchObject({
      sub: decodeToken(tokens.access_token).payload.sub,
      email: fixture.user.email,
      tenant: 'acme',
    });
  });
});

describe('Lightweight JWT - toggle', () => {
  it('should restore the custom claim in the access token when disabled, and remove it when enabled again', async () => {
    await fixture.setLightweightJwt(false, 'tenant');
    const full = await fixture.passwordGrant(SCOPE);
    expect(decodeToken(full.access_token).payload.tenant).toEqual('acme');

    await fixture.setLightweightJwt(true, 'tenant');
    const lightweight = await fixture.passwordGrant(SCOPE);
    expect(claimNames(lightweight.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
  });
});
