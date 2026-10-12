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
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

const SCOPE = 'openid email profile';
const ROLE = 'introspection-role';
const GROUP = 'introspection-group';
const ACCESS_TOKEN_ALLOWLIST = ['tenant'];
const PER_TOKEN_CLAIMS = ['exp', 'iat', 'jti'];

let fixture: TokenIdentityFixture;
let fullIntrospection: Record<string, any>;

function withoutPerTokenClaims(claims: Record<string, any>): Record<string, any> {
  return Object.fromEntries(Object.entries(claims).filter(([name]) => !PER_TOKEN_CLAIMS.includes(name)));
}

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    userAttributes: { department: 'engineering' },
    userRole: ROLE,
    userGroup: GROUP,
    tokenPreScript: "context.setAttribute('preTokenValue', 'from-pre-token')",
    lightweightJwtSettings: { enabled: false, allowedAccessTokenClaims: ACCESS_TOKEN_ALLOWLIST },
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'tenant', claimValue: 'acme' },
      { tokenType: 'ACCESS_TOKEN', claimName: 'region', claimValue: 'eu' },
      {
        tokenType: 'ACCESS_TOKEN',
        claimName: 'department',
        claimValue: "{#context.attributes['user'].additionalInformation['department']}",
      },
      { tokenType: 'ACCESS_TOKEN', claimName: 'user_roles', claimValue: "{#context.attributes['user'].roles}" },
      { tokenType: 'ACCESS_TOKEN', claimName: 'user_groups', claimValue: "{#context.attributes['user'].groups}" },
      { tokenType: 'ACCESS_TOKEN', claimName: 'pre_token', claimValue: "{#context.attributes['preTokenValue']}" },
    ],
    userinfoCustomClaims: [
      { claimName: 'tenant', claimValue: 'acme' },
      { claimName: 'department', claimValue: "{#context.attributes['user'].additionalInformation['department']}" },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Lightweight JWT introspection - disabled', () => {
  it('should introspect a full access token with its claims', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;

    fullIntrospection = await fixture.introspect(accessToken);

    expect(fullIntrospection).toMatchObject({
      active: true,
      tenant: 'acme',
      region: 'eu',
      department: 'engineering',
      pre_token: 'from-pre-token',
      user_roles: [ROLE],
    });
    expect(fullIntrospection.user_groups).toHaveLength(1);
  });
});

describe('Lightweight JWT introspection - enabled', () => {
  beforeAll(async () => {
    await fixture.setLightweightJwt(true, 'region', { allowedAccessTokenClaims: ACCESS_TOKEN_ALLOWLIST });
  });

  it('should return the claims a full access token carries', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;
    const payload = decodeToken(accessToken).payload;
    expect(payload).not.toHaveProperty('region');
    expect(payload).not.toHaveProperty('department');

    const introspection = await fixture.introspect(accessToken);

    expect(introspection.active).toBe(true);
    expect(withoutPerTokenClaims(introspection)).toEqual(withoutPerTokenClaims(fullIntrospection));
    expect(introspection).toMatchObject({ jti: payload.jti, iat: payload.iat, exp: payload.exp });
  });

  it('should return the claims the access token allowlist keeps and the ones it removes', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;
    expect(decodeToken(accessToken).payload.tenant).toEqual('acme');

    expect(await fixture.introspect(accessToken, 'access_token')).toMatchObject({ active: true, tenant: 'acme', region: 'eu' });
  });

  it('should return the full claim set for an access token from the refresh token grant', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);
    expect(decodeToken(refreshed.access_token).payload).not.toHaveProperty('region');

    const introspection = await fixture.introspect(refreshed.access_token);

    expect(withoutPerTokenClaims(introspection)).toEqual(withoutPerTokenClaims(fullIntrospection));
  });

  it('should return the claims removed from the refresh token', async () => {
    const refreshToken = (await fixture.passwordGrant(SCOPE)).refresh_token;
    expect(decodeToken(refreshToken).payload).not.toHaveProperty('region');

    expect(await fixture.introspect(refreshToken, 'refresh_token')).toMatchObject({
      active: true,
      tenant: 'acme',
      region: 'eu',
      department: 'engineering',
    });
  });

  it('should return claims consistent with UserInfo', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;

    const introspection = await fixture.introspect(accessToken);
    const userinfo = await fixture.userinfo(accessToken);

    expect(userinfo).toMatchObject({ sub: introspection.sub, tenant: introspection.tenant, department: introspection.department });
  });

  it('should report a revoked access token as inactive', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;

    await fixture.revoke(accessToken, 'access_token');

    expect(await fixture.introspect(accessToken)).toEqual({ active: false });
  });

  it('should report a revoked refresh token as inactive', async () => {
    const refreshToken = (await fixture.passwordGrant(SCOPE)).refresh_token;

    await fixture.revoke(refreshToken, 'refresh_token');

    expect(await fixture.introspect(refreshToken, 'refresh_token')).toEqual({ active: false });
  });

  it('should report a tampered access token as inactive', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;
    const [header, payload] = accessToken.split('.');

    expect(await fixture.introspect(`${header}.${payload}.invalid-signature`)).toEqual({ active: false });
  });
});
