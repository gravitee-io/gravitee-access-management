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
import {
  cnfJkt,
  clientCredentialsToken,
  DpopFixture,
  getAuthorizationCode,
  passwordToken,
  redeemAuthorizationCode,
  refreshAccessToken,
  setupDpopFixture,
  userInfo,
} from './fixture/dpop-fixture';
import { createDpopKey } from './dpop-proof';

setup(200000);

let fixture: DpopFixture;

beforeAll(async () => {
  fixture = await setupDpopFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('DPoP (RFC 9449) — token-endpoint key binding', () => {
  it('binds the access token to the proof key when a proof is presented', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const response = await clientCredentialsToken(fixture, fixture.ccApp, proof);

    expect(response.status).toBe(200);
    expect(response.body.token_type).toBe('DPoP');
    expect(cnfJkt(response.body.access_token)).toBe(key.jkt);
  });

  it('issues a plain bearer token when no proof is presented (opportunistic)', async () => {
    const response = await clientCredentialsToken(fixture, fixture.ccApp);

    expect(response.status).toBe(200);
    expect(response.body.token_type).toBe('bearer');
  });

  it('rejects a proof whose htu does not match the token endpoint', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: 'https://attacker.example/token', htm: 'POST' });
    const response = await clientCredentialsToken(fixture, fixture.ccApp, proof);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_dpop_proof');
  });
});

describe('DPoP (RFC 9449) — proof replay', () => {
  it('rejects a proof that has already been used', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });

    const first = await clientCredentialsToken(fixture, fixture.ccApp, proof);
    expect(first.status).toBe(200);

    const second = await clientCredentialsToken(fixture, fixture.ccApp, proof);
    expect(second.status).toBe(400);
    expect(second.body.error).toBe('invalid_dpop_proof');
  });
});

describe('DPoP (RFC 9449) — refresh-token key continuity', () => {
  it('keeps the token bound to the original key across two consecutive refreshes', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });

    const initial = await passwordToken(fixture, proof);
    expect(initial.status).toBe(200);
    expect(initial.body.token_type).toBe('DPoP');
    expect(cnfJkt(initial.body.access_token)).toBe(key.jkt);

    let refreshToken = initial.body.refresh_token;
    for (let i = 0; i < 2; i++) {
      const refreshProof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
      const refreshed = await refreshAccessToken(fixture, refreshToken, refreshProof);

      expect(refreshed.status).toBe(200);
      expect(refreshed.body.token_type).toBe('DPoP');
      expect(cnfJkt(refreshed.body.access_token)).toBe(key.jkt);
      refreshToken = refreshed.body.refresh_token ?? refreshToken;
    }
  });

  it('rejects a refresh of a DPoP-bound token when the proof is signed by a different key', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const initial = await passwordToken(fixture, proof);
    expect(initial.status).toBe(200);
    expect(initial.body.token_type).toBe('DPoP');

    const otherKey = await createDpopKey();
    const wrongKeyProof = await otherKey.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const refreshed = await refreshAccessToken(fixture, initial.body.refresh_token, wrongKeyProof);

    expect(refreshed.status).toBe(400);
    expect(refreshed.body.error).toBe('invalid_dpop_proof');
  });

  it('rejects a refresh of a DPoP-bound token when no proof is presented', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const initial = await passwordToken(fixture, proof);
    expect(initial.status).toBe(200);
    expect(initial.body.token_type).toBe('DPoP');

    const refreshed = await refreshAccessToken(fixture, initial.body.refresh_token);

    expect(refreshed.status).toBe(400);
    expect(refreshed.body.error).toBe('invalid_request');
  });
});

describe('DPoP (RFC 9449 §10) — authorization-code binding via dpop_jkt', () => {
  it('binds the token to the committed dpop_jkt with a matching-key proof', async () => {
    const key = await createDpopKey();
    const code = await getAuthorizationCode(fixture, key.jkt);
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });

    const response = await redeemAuthorizationCode(fixture, code, proof);
    expect(response.status).toBe(200);
    expect(response.body.token_type).toBe('DPoP');
    expect(cnfJkt(response.body.access_token)).toBe(key.jkt);
  });

  it('rejects redemption of a dpop_jkt-bound code with no proof', async () => {
    const key = await createDpopKey();
    const code = await getAuthorizationCode(fixture, key.jkt);

    const response = await redeemAuthorizationCode(fixture, code);
    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_dpop_proof');
  });

  it('rejects redemption of a dpop_jkt-bound code with a wrong-key proof', async () => {
    const committedKey = await createDpopKey();
    const otherKey = await createDpopKey();
    const code = await getAuthorizationCode(fixture, committedKey.jkt);
    const proof = await otherKey.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });

    const response = await redeemAuthorizationCode(fixture, code, proof);
    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_dpop_proof');
  });

  it('falls back to opportunistic binding when no dpop_jkt is committed', async () => {
    const key = await createDpopKey();
    const code = await getAuthorizationCode(fixture);
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });

    const response = await redeemAuthorizationCode(fixture, code, proof);
    expect(response.status).toBe(200);
    expect(response.body.token_type).toBe('DPoP');
    expect(cnfJkt(response.body.access_token)).toBe(key.jkt);
  });
});

describe('DPoP (RFC 9449) — per-application requirement', () => {
  it('rejects a token request without a proof when the app requires DPoP', async () => {
    // A wholly absent DPoP header on a require-DPoP app is a malformed request (invalid_request);
    // a present-but-invalid proof is invalid_dpop_proof (see the token-endpoint binding cases).
    const response = await clientCredentialsToken(fixture, fixture.requireApp);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_request');
  });

  it('issues a DPoP-bound token when the required proof is present', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const response = await clientCredentialsToken(fixture, fixture.requireApp, proof);

    expect(response.status).toBe(200);
    expect(response.body.token_type).toBe('DPoP');
    expect(cnfJkt(response.body.access_token)).toBe(key.jkt);
  });
});

describe('DPoP (RFC 9449 §9) — resource access at /userinfo', () => {
  it('accepts a DPoP-bound token presented with a valid ath proof', async () => {
    const key = await createDpopKey();
    const tokenProof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const accessToken = (await passwordToken(fixture, tokenProof)).body.access_token;
    const userInfoProof = await key.proof({ htu: fixture.oidc.userinfo_endpoint, htm: 'GET', accessToken });

    const response = await userInfo(fixture, 'DPoP ' + accessToken, userInfoProof);
    expect(response.status).toBe(200);
    expect(response.body.sub).toBeDefined();
  });

  it('rejects a DPoP-bound token presented under the Bearer scheme (downgrade guard)', async () => {
    const key = await createDpopKey();
    const tokenProof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const accessToken = (await passwordToken(fixture, tokenProof)).body.access_token;

    const response = await userInfo(fixture, 'Bearer ' + accessToken);
    expect(response.status).toBe(401);
    expect(response.headers['www-authenticate']).toContain('DPoP');
    expect(response.headers['www-authenticate']).toContain('error="invalid_token"');
  });
});
