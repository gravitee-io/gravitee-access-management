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
import { cnfJkt, clientCredentialsToken, DpopFixture, setupDpopFixture } from '../dpop/fixture/dpop-fixture';
import { createDpopKey } from '../dpop/dpop-proof';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

// RFC 9449 sender-constrained tokens are only enforceable if the resource server can discover the
// key a token is bound to. A resource server that does not parse the JWT itself learns this from
// the introspection response's `cnf.jkt` — so that claim has to survive the round trip through
// IntrospectionServiceImpl, which copies it from the AccessToken's confirmation method.
//
// The existing DPoP suite reads `cnf.jkt` off the raw access token, which proves the token endpoint
// binds correctly but says nothing about what introspection reports. That is the gap these cover.

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

const introspect = (token: string) =>
  performPost(fixture.oidc.introspection_endpoint, '', `token=${encodeURIComponent(token)}`, {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(fixture.ccApp),
  });

describe('Introspection - DPoP sender-constrained tokens', () => {
  it('reports the confirmation key of a DPoP-bound token', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' });
    const tokenResponse = await clientCredentialsToken(fixture, fixture.ccApp, proof);
    expect(tokenResponse.status).toBe(200);

    const accessToken = tokenResponse.body.access_token;
    // Sanity: the token endpoint bound the token, so introspection has something to report.
    expect(cnfJkt(accessToken)).toBe(key.jkt);

    const introspection = await introspect(accessToken).expect(200);

    expect(introspection.body.active).toBe(true);
    expect(introspection.body.cnf).toBeDefined();
    expect(introspection.body.cnf.jkt).toBe(key.jkt);
  });

  it('reports no confirmation key for a token issued without a DPoP proof', async () => {
    const tokenResponse = await clientCredentialsToken(fixture, fixture.ccApp);
    expect(tokenResponse.status).toBe(200);

    const accessToken = tokenResponse.body.access_token;
    expect(cnfJkt(accessToken)).toBeUndefined();

    const introspection = await introspect(accessToken).expect(200);

    expect(introspection.body.active).toBe(true);
    expect(introspection.body.cnf).toBeUndefined();
  });

  it('reports distinct confirmation keys for tokens bound to different keys', async () => {
    const firstKey = await createDpopKey();
    const secondKey = await createDpopKey();
    expect(firstKey.jkt).not.toBe(secondKey.jkt);

    const firstToken = (
      await clientCredentialsToken(fixture, fixture.ccApp, await firstKey.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' }))
    ).body.access_token;
    const secondToken = (
      await clientCredentialsToken(fixture, fixture.ccApp, await secondKey.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' }))
    ).body.access_token;

    const firstIntrospection = await introspect(firstToken).expect(200);
    const secondIntrospection = await introspect(secondToken).expect(200);

    expect(firstIntrospection.body.cnf.jkt).toBe(firstKey.jkt);
    expect(secondIntrospection.body.cnf.jkt).toBe(secondKey.jkt);
  });
});
