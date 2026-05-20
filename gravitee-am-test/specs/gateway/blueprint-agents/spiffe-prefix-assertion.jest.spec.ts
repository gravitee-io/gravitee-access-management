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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { decodeJwt } from '@utils-commands/jwt';
import { setup } from '../../test-fixture';
import { SpiffePrefixFixture, setupSpiffePrefixFixture } from './fixtures/spiffe-prefix-fixture';

setup(120000);

const JWT_SPIFFE_ASSERTION_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-spiffe';
const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

/**
 * GMA-329 — SPIFFE per-instance agent identity.
 *
 * Requires the local-stack to be running with the SPIRE overlay AND
 * RUN_SPIRE_TESTS=true in the environment (skipped otherwise so CI stays green):
 *   docker compose -f docker/local-stack/dev/docker-compose.yml \
 *                  -f docker/local-stack/dev/docker-compose-dev.yml \
 *                  -f docker/local-stack/dev/docker-compose.mongo.yml \
 *                  -f docker/local-stack/dev/docker-compose.spire.yml up -d
 *   RUN_SPIRE_TESTS=true npm --prefix gravitee-am-test run test -- \
 *     specs/gateway/blueprint-agents/spiffe-prefix-assertion.jest.spec.ts
 *
 * SPIRE bootstrap provisions:
 *   - spiffe://am.local/agent/test/sample (under the configured PREFIX)
 *   - spiffe://am.local/agent/billing      (outside the PREFIX — negative case)
 */
const describeIfSpire = process.env.RUN_SPIRE_TESTS === 'true' ? describe : describe.skip;

describeIfSpire('Blueprint Agent — SPIFFE PREFIX subject matching', () => {
  let fixture: SpiffePrefixFixture;

  beforeAll(async () => {
    fixture = await setupSpiffePrefixFixture();
  });

  afterAll(async () => {
    await fixture?.cleanUp();
  });

  function postClientAssertion(assertion: string) {
    // Pin the client_id to our blueprint so client lookup resolves to it; otherwise
    // the validator falls back to looking the client up by the SVID's `sub`, which
    // would mask the PREFIX-mismatch path in the negative case below.
    const body =
      `grant_type=client_credentials` +
      `&client_id=${encodeURIComponent(fixture.clientId)}` +
      `&client_assertion_type=${encodeURIComponent(JWT_SPIFFE_ASSERTION_TYPE)}` +
      `&client_assertion=${encodeURIComponent(assertion)}`;
    return performPost(fixture.oidc.token_endpoint, '', body, {
      'Content-type': 'application/x-www-form-urlencoded',
    });
  }

  it('mints a per-instance token when the SVID is under the configured subject prefix', async () => {
    const instanceId = 'spiffe://am.local/agent/test/sample';
    const svid = fixture.fetchSvid(instanceId, fixture.oidc.token_endpoint);

    const response = await postClientAssertion(svid).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);
    expect(response.body.token_type).toEqual('bearer');

    const decoded = decodeJwt(response.body.access_token);
    expect(decoded.sub).toEqual(instanceId);
    expect(decoded.act).toBeDefined();
    expect((decoded.act as any).sub).toEqual(fixture.clientId);
  });

  it('rejects SVIDs whose sub falls outside the configured subject prefix', async () => {
    // Provisioned but unrelated to fixture.prefixSubject (spiffe://am.local/agent/test).
    const outOfPrefix = 'spiffe://am.local/agent/billing';
    const svid = fixture.fetchSvid(outOfPrefix, fixture.oidc.token_endpoint);

    await postClientAssertion(svid).expect(401);
  });
});
