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
import { compactDecrypt, createRemoteJWKSet, exportJWK, generateKeyPair, jwtVerify, KeyLike } from 'jose';
import { performGet, performPatch } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);
retryImmediatelyForThisFile();

const SCOPE = 'openid email profile';
const ALLOWLISTS = { allowedAccessTokenClaims: new Set(['tenant']), allowedIdTokenClaims: new Set(['email']) };

let fixture: TokenIdentityFixture;
let fullUserinfo: Record<string, any>;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    lightweightJwtSettings: { enabled: false, ...ALLOWLISTS },
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'tenant', claimValue: 'acme' },
      { tokenType: 'ACCESS_TOKEN', claimName: 'region', claimValue: 'eu' },
    ],
    userinfoCustomClaims: [
      { claimName: 'token_tenant', claimValue: "{#context.attributes['token']['tenant']}" },
      { claimName: 'token_region', claimValue: "{#context.attributes['token']['region']}" },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function userinfoResponse(accessToken: string) {
  return performGet(fixture.oidc.userinfo_endpoint, '', { Authorization: `Bearer ${accessToken}` }).buffer(true);
}

async function requestSignedAndEncryptedUserinfo(): Promise<KeyLike> {
  const { publicKey, privateKey } = await generateKeyPair('RSA-OAEP-256', { modulusLength: 2048 });
  const jwk = { ...(await exportJWK(publicKey)), kid: 'userinfo-encryption', use: 'enc' };
  const applicationUrl =
    `/management/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}` +
    `/domains/${fixture.domain.id}/applications/${fixture.app.id}`;
  await waitForSyncAfter(fixture.domain.id, () =>
    performPatch(
      process.env.AM_MANAGEMENT_URL,
      applicationUrl,
      {
        settings: {
          oauth: {
            jwks: { keys: [jwk] },
            userinfoSignedResponseAlg: 'RS256',
            userinfoEncryptedResponseAlg: 'RSA-OAEP-256',
            userinfoEncryptedResponseEnc: 'A128CBC-HS256',
          },
        },
      },
      { 'Content-Type': 'application/json', Authorization: `Bearer ${fixture.accessToken}` },
    ).expect(200),
  );

  const deadline = Date.now() + 15_000;
  for (;;) {
    const response = await userinfoResponse((await fixture.passwordGrant(SCOPE)).access_token).expect(200);
    if (response.headers['content-type'].startsWith('application/jwt')) {
      return privateKey;
    }
    if (Date.now() > deadline) {
      throw new Error('gateway still not serving a signed and encrypted UserInfo after 15s');
    }
    await sleep(500);
  }
}

describe('Lightweight JWT UserInfo - disabled', () => {
  it('should answer UserInfo with the profile claims and the custom claims read from the access token', async () => {
    fullUserinfo = await fixture.userinfo((await fixture.passwordGrant(SCOPE)).access_token);

    expect(fullUserinfo).toMatchObject({
      email: fixture.user.email,
      given_name: fixture.user.firstName,
      family_name: fixture.user.lastName,
      token_tenant: 'acme',
      token_region: 'eu',
    });
  });
});

describe('Lightweight JWT UserInfo - enabled', () => {
  beforeAll(async () => {
    await fixture.setLightweightJwt(true, 'region', ALLOWLISTS);
  });

  it('should answer UserInfo as for a full access token, whatever the allowlists keep', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;
    const payload = decodeToken(accessToken).payload;
    expect(payload.tenant).toEqual('acme');
    expect(payload).not.toHaveProperty('region');
    expect(payload).not.toHaveProperty('email');

    expect(await fixture.userinfo(accessToken)).toEqual(fullUserinfo);
  });

  it('should answer UserInfo as for a full access token issued by the refresh token grant', async () => {
    const initial = await fixture.passwordGrant(SCOPE);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);
    expect(decodeToken(refreshed.access_token).payload).not.toHaveProperty('region');

    expect(await fixture.userinfo(refreshed.access_token)).toEqual(fullUserinfo);
  });

  it('should return the claims introspection returns for the same access token', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;

    const introspection = await fixture.introspect(accessToken);

    expect(await fixture.userinfo(accessToken)).toMatchObject({
      sub: introspection.sub,
      token_tenant: introspection.tenant,
      token_region: introspection.region,
    });
  });

  it('should reject a revoked access token', async () => {
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;

    await fixture.revoke(accessToken, 'access_token');

    await userinfoResponse(accessToken).expect(401);
  });

  it('should reject a tampered access token', async () => {
    const [header, payload] = (await fixture.passwordGrant(SCOPE)).access_token.split('.');

    await userinfoResponse(`${header}.${payload}.invalid-signature`).expect(401);
  });

  it('should answer a signed and encrypted UserInfo as for a full access token', async () => {
    const privateKey = await requestSignedAndEncryptedUserinfo();
    const accessToken = (await fixture.passwordGrant(SCOPE)).access_token;

    const response = await userinfoResponse(accessToken).expect(200);

    const { plaintext } = await compactDecrypt(response.text, privateKey);
    const { payload } = await jwtVerify(new TextDecoder().decode(plaintext), createRemoteJWKSet(new URL(fixture.oidc.jwks_uri)), {
      issuer: fixture.oidc.issuer,
      audience: fixture.clientId,
    });
    const { updated_at: updatedAt, ...claims } = fullUserinfo;
    expect(payload).toMatchObject(claims);
    expect(payload.updated_at).toEqual(updatedAt && Math.floor(updatedAt / 1000));
    expect(payload.exp).toEqual(decodeToken(accessToken).payload.exp);
  });
});
