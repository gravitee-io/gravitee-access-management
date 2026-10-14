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
import { updateUser } from '@management-commands/user-management-commands';
import { setup } from '../../test-fixture';
import {
  decodeToken,
  LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS,
  LIGHTWEIGHT_ID_TOKEN_CLAIMS,
  LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS,
  setupTokenIdentityFixture,
  TokenIdentityFixture,
} from './fixtures/token-identity-fixture';

setup(200000);

const SCOPE = 'openid email profile';
const NO_ALLOWLISTS = { allowedAccessTokenClaims: [], allowedIdTokenClaims: [] };
const TOKEN_CUSTOM_CLAIMS = [
  { tokenType: 'ACCESS_TOKEN' as const, claimName: 'tenant', claimValue: 'acme' },
  { tokenType: 'ACCESS_TOKEN' as const, claimName: 'region', claimValue: 'eu' },
  { tokenType: 'ID_TOKEN' as const, claimName: 'id_tenant', claimValue: 'acme' },
];

function claimNames(token: string): string[] {
  return Object.keys(decodeToken(token).payload).sort();
}

describe('Lightweight JWT - refresh token grant', () => {
  let fixture: TokenIdentityFixture;

  beforeAll(async () => {
    fixture = await setupTokenIdentityFixture({
      lightweightJwtSettings: { enabled: true },
      tokenCustomClaims: TOKEN_CUSTOM_CLAIMS,
      userinfoCustomClaims: [{ claimName: 'tenant', claimValue: 'acme' }],
    });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it('should keep the granted scopes when the refresh requests none', async () => {
    const initial = await fixture.passwordGrant(SCOPE);

    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    const grantedScopes = decodeToken(initial.access_token).payload.scope.split(' ').sort();
    expect(decodeToken(refreshed.access_token).payload.scope.split(' ').sort()).toEqual(grantedScopes);
    expect(decodeToken(refreshed.refresh_token).payload.scope.split(' ').sort()).toEqual(grantedScopes);
  });

  it('should keep the lightweight rules when the refresh narrows the scope', async () => {
    const initial = await fixture.passwordGrant(SCOPE);

    const refreshed = await fixture.refreshGrant(initial.refresh_token, 'openid');

    expect(refreshed.scope).toEqual('openid');
    expect(decodeToken(refreshed.access_token).payload.scope).toEqual('openid');
    expect(claimNames(refreshed.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
    expect(claimNames(refreshed.refresh_token)).toEqual(LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS);
    expect(claimNames(refreshed.id_token)).toEqual(LIGHTWEIGHT_ID_TOKEN_CLAIMS);
  });

  it('should not return an ID token when the refresh drops the openid scope', async () => {
    const initial = await fixture.passwordGrant(SCOPE);

    const refreshed = await fixture.refreshGrant(initial.refresh_token, 'email');

    expect(refreshed).not.toHaveProperty('id_token');
    expect(claimNames(refreshed.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
  });

  it('should introspect the refreshed access token', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    const introspection = await fixture.introspect(refreshed.access_token);

    const accessToken = decodeToken(refreshed.access_token).payload;
    expect(introspection).toMatchObject({ active: true, sub: accessToken.sub, client_id: fixture.clientId, scope: accessToken.scope });
  });

  it('should answer UserInfo with the full claim set for the refreshed access token', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    const claims = await fixture.userinfo(refreshed.access_token);

    expect(claims).toMatchObject({
      email: fixture.user.email,
      given_name: fixture.user.firstName,
      family_name: fixture.user.lastName,
      tenant: 'acme',
    });
  });

  it('should answer UserInfo with the user data current at refresh time', async () => {
    const subject = fixture.otherUser;
    const initial = await fixture.passwordGrantFor(subject, SCOPE);
    const familyName = `Renamed${Date.now()}`;
    await updateUser(fixture.domain.id, fixture.accessToken, subject.id, {
      firstName: subject.firstName,
      lastName: familyName,
      email: subject.email,
    });

    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(claimNames(refreshed.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
    expect(await fixture.userinfo(refreshed.access_token)).toMatchObject({ family_name: familyName });
  });

  it('should reject a lightweight refresh token that was already rotated', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    await fixture.refreshGrant(initial.refresh_token);

    const reuse = await fixture.refreshGrantResponse(initial.refresh_token);

    expect(reuse.status).toEqual(400);
    expect(reuse.body.error).toEqual('invalid_grant');
  });

  it('should reject a lightweight refresh token that was revoked', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    await fixture.revoke(initial.refresh_token);

    const refresh = await fixture.refreshGrantResponse(initial.refresh_token);

    expect(refresh.status).toEqual(400);
    expect(refresh.body.error).toEqual('invalid_grant');
  });
});

describe('Lightweight JWT - settings changed between issuance and refresh', () => {
  let fixture: TokenIdentityFixture;

  beforeAll(async () => {
    fixture = await setupTokenIdentityFixture({
      lightweightJwtSettings: { enabled: true },
      tokenCustomClaims: TOKEN_CUSTOM_CLAIMS,
    });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it('should apply the allowlists configured at refresh time', async () => {
    await fixture.setLightweightJwt(true, 'region', { allowedAccessTokenClaims: ['tenant'], allowedIdTokenClaims: ['email'] });
    const initial = await fixture.passwordGrant(SCOPE);
    expect(decodeToken(initial.refresh_token).payload.tenant).toEqual('acme');
    await fixture.setLightweightJwt(true, 'tenant', { allowedAccessTokenClaims: ['region'], allowedIdTokenClaims: ['id_tenant'] });

    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    const accessToken = decodeToken(refreshed.access_token).payload;
    expect(accessToken.region).toEqual('eu');
    expect(accessToken).not.toHaveProperty('tenant');
    const refreshToken = decodeToken(refreshed.refresh_token).payload;
    expect(refreshToken.region).toEqual('eu');
    expect(refreshToken).not.toHaveProperty('tenant');
    const idToken = decodeToken(refreshed.id_token).payload;
    expect(idToken.id_tenant).toEqual('acme');
    expect(idToken).not.toHaveProperty('email');
  });

  it('should issue lightweight tokens from a full refresh token once Lightweight JWT is enabled', async () => {
    await fixture.setLightweightJwt(false, 'region', NO_ALLOWLISTS);
    const initial = await fixture.passwordGrant(SCOPE);
    expect(decodeToken(initial.refresh_token).payload).toMatchObject({ tenant: 'acme', region: 'eu' });
    await fixture.setLightweightJwt(true, 'region', NO_ALLOWLISTS);

    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(claimNames(refreshed.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
    expect(claimNames(refreshed.refresh_token)).toEqual(LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS);
    expect(claimNames(refreshed.id_token)).toEqual(LIGHTWEIGHT_ID_TOKEN_CLAIMS);
  });

  it('should issue full tokens from a lightweight refresh token once Lightweight JWT is disabled', async () => {
    await fixture.setLightweightJwt(true, 'region', NO_ALLOWLISTS);
    const initial = await fixture.passwordGrant(SCOPE);
    expect(claimNames(initial.refresh_token)).toEqual(LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS);
    await fixture.setLightweightJwt(false, 'region', NO_ALLOWLISTS);

    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(decodeToken(refreshed.access_token).payload).toMatchObject({ tenant: 'acme', region: 'eu' });
    expect(decodeToken(refreshed.refresh_token).payload).toMatchObject({ tenant: 'acme', region: 'eu' });
    expect(decodeToken(refreshed.id_token).payload).toMatchObject({ id_tenant: 'acme', given_name: fixture.user.firstName });
  });
});

describe('Lightweight JWT - refresh token rotation disabled', () => {
  let fixture: TokenIdentityFixture;

  beforeAll(async () => {
    fixture = await setupTokenIdentityFixture({
      lightweightJwtSettings: { enabled: true },
      tokenCustomClaims: TOKEN_CUSTOM_CLAIMS,
      disableRefreshTokenRotation: true,
    });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it('should return the same lightweight refresh token and accept it again', async () => {
    const initial = await fixture.passwordGrant(SCOPE);

    const first = await fixture.refreshGrant(initial.refresh_token);
    const second = await fixture.refreshGrant(initial.refresh_token);

    expect(first.refresh_token).toEqual(initial.refresh_token);
    expect(claimNames(first.refresh_token)).toEqual(LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS);
    expect(claimNames(second.access_token)).toEqual(LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
  });
});
