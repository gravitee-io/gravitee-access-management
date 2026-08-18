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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * Application custom claims are applied last, after AM has built the token, so
 * they take precedence over generated values. These tests cover:
 *
 *  - `gis` is refused at configuration time, because AM resolves the user from it
 *  - a custom claim reaches both token types and the token still verifies
 *  - `gis` continues to carry the true identity
 */
const HIJACK_VALUE = 'custom-claim-value';

let fixture: TokenIdentityFixture;
let jwks: ReturnType<typeof createRemoteJWKSet>;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'department', claimValue: HIJACK_VALUE },
      { tokenType: 'ID_TOKEN', claimName: 'department', claimValue: HIJACK_VALUE },
    ],
  });
  jwks = createRemoteJWKSet(new URL(fixture.oidc.jwks_uri));
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Custom claims - reaching the relying party', () => {
  it('delivers a configured claim in both the access token and the ID token', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    expect(decodeToken(tokens.access_token).payload.department).toEqual(HIJACK_VALUE);
    expect(decodeToken(tokens.id_token).payload.department).toEqual(HIJACK_VALUE);
  });

  it('leaves the token verifiable by a relying party', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    const { payload } = await jwtVerify(tokens.access_token, jwks, {
      issuer: fixture.oidc.issuer,
      audience: fixture.clientId,
    });

    expect(payload.department).toEqual(HIJACK_VALUE);
  });

  it('still resolves the real user, so userinfo is unaffected by the added claim', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.sub).toEqual(decodeToken(tokens.access_token).payload.sub);
    expect(profile.email).toEqual(fixture.user.email);
  });
});

describe('Custom claims - the identity anchor is protected at configuration time', () => {
  let adminToken: string;
  let guardDomainId: string | undefined;

  beforeAll(async () => {
    adminToken = await requestAdminAccessToken();
  });

  afterAll(async () => {
    if (guardDomainId) {
      await safeDeleteDomain(guardDomainId, adminToken);
    }
  });

  it('refuses an application configured with a custom claim named gis', async () => {
    const domain = await createDomain(adminToken, uniqueName('claim-guard', true), 'Reserved claim name guard');
    guardDomainId = domain.id;

    const idpSet = await getAllIdps(domain.id, adminToken);
    const defaultIdp = idpSet.values().next().value;

    await expect(
      createTestApp('claim-guard-app', domain, adminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: ['https://example.com/callback'],
            grantTypes: ['password'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
            tokenCustomClaims: [{ tokenType: 'ACCESS_TOKEN', claimName: 'gis', claimValue: 'spoofed' }],
          },
        },
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Invalid token claims'),
    });
  });
});
