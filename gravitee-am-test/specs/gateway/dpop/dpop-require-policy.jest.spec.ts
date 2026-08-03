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
import { cnfJkt, clientCredentialsToken, DpopRequiredFixture, setupDpopRequiredFixture, userInfo } from './fixture/dpop-fixture';
import { createDpopKey } from './dpop-proof';

setup(200000);

let fixture: DpopRequiredFixture;

beforeAll(async () => {
  fixture = await setupDpopRequiredFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('DPoP (RFC 9449 §8) — domain-wide requirement (floor) at the token endpoint', () => {
  it('rejects a token request without a proof for any application', async () => {
    const response = await clientCredentialsToken(fixture, fixture.floorApp);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_request');
  });

  it('issues a DPoP-bound token when a proof is present', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const response = await clientCredentialsToken(fixture, fixture.floorApp, proof);

    expect(response.status).toBe(200);
    expect(response.body.token_type).toBe('DPoP');
    expect(cnfJkt(response.body.access_token)).toBe(key.jkt);
  });
});

describe('DPoP (RFC 9449 §9) — resource enforcement under the floor', () => {
  it('challenges an unauthenticated /userinfo request with the Bearer scheme', async () => {
    // Finding: the domain-wide DPoP floor does NOT escalate the anonymous challenge to the DPoP
    // scheme — an unauthenticated request still gets the default Bearer challenge, identical to a
    // require-off domain. The DPoP challenge is emitted only for a mis-presented sender-constrained
    // token (covered on Domain A). The floor's real teeth are at the token endpoint (above).
    const response = await userInfo(fixture);

    expect(response.status).toBe(401);
    expect(response.headers['www-authenticate']).toBe('Bearer realm="gravitee-io"');
  });
});
