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
import { cnfJkt, clientCredentialsToken, DpopAllowlistFixture, setupDpopAllowlistFixture } from './fixture/dpop-fixture';
import { createDpopKey } from './dpop-proof';

setup(200000);

let fixture: DpopAllowlistFixture;

beforeAll(async () => {
  fixture = await setupDpopAllowlistFixture(['ES256']);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('DPoP (RFC 9449) — signing-algorithm allowlist', () => {
  it('accepts a proof signed with an allowed algorithm (ES256)', async () => {
    const key = await createDpopKey('ES256');
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const response = await clientCredentialsToken(fixture, fixture.algApp, proof);

    expect(response.status).toBe(200);
    expect(response.body.token_type).toBe('DPoP');
    expect(cnfJkt(response.body.access_token)).toBe(key.jkt);
  });

  it('rejects a proof signed with a disallowed algorithm (RS256)', async () => {
    const key = await createDpopKey('RS256');
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const response = await clientCredentialsToken(fixture, fixture.algApp, proof);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_dpop_proof');
  });
});

describe('DPoP (RFC 9449) — discovery', () => {
  it('advertises dpop_signing_alg_values_supported reflecting the domain allowlist', () => {
    expect(fixture.oidc.dpop_signing_alg_values_supported).toEqual(['ES256']);
  });
});

// NB: the per-domain allowlist constrains the token endpoint and discovery (above), but the
// resource-side WWW-Authenticate still advertises the server's full supported-alg set, not the
// domain allowlist — so there is no resource-challenge assertion here. See ticket 03 notes.
