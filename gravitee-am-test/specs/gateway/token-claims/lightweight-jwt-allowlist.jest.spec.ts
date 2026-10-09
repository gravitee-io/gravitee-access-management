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
import { getApplication, patchApplication } from '@management-commands/application-management-commands';
import { setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

const SCOPE = 'openid email profile';
const ALLOWLISTS = {
  accessTokenAllowlist: ['tenant', 'not_issued'],
  idTokenAllowlist: ['email', 'id_tenant'],
};

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    lightweightJwtSettings: { enabled: true, ...ALLOWLISTS },
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'tenant', claimValue: 'acme' },
      { tokenType: 'ACCESS_TOKEN', claimName: 'region', claimValue: 'eu' },
      { tokenType: 'ID_TOKEN', claimName: 'id_tenant', claimValue: 'acme' },
      { tokenType: 'ID_TOKEN', claimName: 'region', claimValue: 'eu' },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Lightweight JWT allowlists - access and refresh tokens', () => {
  it('should keep only the allowlisted claims that AM issues in the access token', async () => {
    const payload = decodeToken((await fixture.passwordGrant(SCOPE)).access_token).payload;

    expect(payload.tenant).toEqual('acme');
    expect(payload).not.toHaveProperty('region');
    expect(payload).not.toHaveProperty('not_issued');
    expect(payload).not.toHaveProperty('email');
  });

  it('should apply the access token allowlist to the refresh token', async () => {
    const payload = decodeToken((await fixture.passwordGrant(SCOPE)).refresh_token).payload;

    expect(payload.tenant).toEqual('acme');
    expect(payload).not.toHaveProperty('region');
  });

  it('should apply the allowlists on the refresh token grant', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(decodeToken(refreshed.access_token).payload.tenant).toEqual('acme');
    expect(decodeToken(refreshed.access_token).payload).not.toHaveProperty('region');
    expect(decodeToken(refreshed.refresh_token).payload.tenant).toEqual('acme');
    expect(decodeToken(refreshed.id_token).payload.email).toEqual(fixture.user.email);
  });
});

describe('Lightweight JWT allowlists - ID token', () => {
  it('should keep only the allowlisted claims that AM issues in the ID token', async () => {
    const payload = decodeToken((await fixture.passwordGrant(SCOPE)).id_token).payload;

    expect(payload.email).toEqual(fixture.user.email);
    expect(payload.id_tenant).toEqual('acme');
    expect(payload).not.toHaveProperty('region');
    expect(payload).not.toHaveProperty('tenant');
    expect(payload).not.toHaveProperty('given_name');
  });
});

describe('Lightweight JWT allowlists - endpoints', () => {
  it('should answer UserInfo with the claims the allowlists remove', async () => {
    const claims = await fixture.userinfo((await fixture.passwordGrant(SCOPE)).access_token);

    expect(claims).toMatchObject({ email: fixture.user.email, given_name: fixture.user.firstName });
  });

  it('should reject a blank claim name', async () => {
    await expect(
      patchApplication(
        fixture.domain.id,
        fixture.accessToken,
        { settings: { oauth: { lightweightJwtSettings: { enabled: true, accessTokenAllowlist: [' '] } } } },
        fixture.app.id,
      ),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });
});

describe('Lightweight JWT allowlists - toggle', () => {
  it('should keep the allowlists when Lightweight JWT is disabled then enabled again', async () => {
    await fixture.setLightweightJwt(false, 'region', ALLOWLISTS);
    const full = decodeToken((await fixture.passwordGrant(SCOPE)).access_token).payload;
    expect(full).toMatchObject({ tenant: 'acme', region: 'eu' });
    const stored = await getApplication(fixture.domain.id, fixture.accessToken, fixture.app.id);
    expect(stored.settings.oauth.lightweightJwtSettings).toMatchObject({ enabled: false, ...ALLOWLISTS });

    await fixture.setLightweightJwt(true, 'region', ALLOWLISTS);
    const lightweight = decodeToken((await fixture.passwordGrant(SCOPE)).access_token).payload;
    expect(lightweight.tenant).toEqual('acme');
    expect(lightweight).not.toHaveProperty('region');
  });
});
