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
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../../test-fixture';
import {
  CimdRevokeOnDocumentChangeFixture,
  setupCimdRevokeOnDocumentChangeFixture,
} from './fixtures/cimd-revoke-on-document-change-fixture';
import { resetAllWireMockScenarios } from './fixtures/cimd-wiremock-helpers';
import { executeCimdAuthCodeFlow } from './fixtures/cimd-auth-flow-helpers';
import { waitFor } from '@management-commands/domain-management-commands';

// Cache TTL configured on the revoke fixture is 1s; wait it out plus a safety buffer.
const CACHE_TTL_EXPIRY_WAIT_MS = 1500;
// Window over which we confirm an erroneous asynchronous revocation does NOT occur.
const NO_REVOCATION_OBSERVATION_MS = 3000;
const REVOCATION_POLL_INTERVAL_MS = 300;

setup(200000);

let fixture: CimdRevokeOnDocumentChangeFixture;

beforeAll(async () => {
  await resetAllWireMockScenarios();
  fixture = await setupCimdRevokeOnDocumentChangeFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD revoke on document change', () => {
  it('should revoke an access token when the CIMD metadata document changes after cache TTL expires', async () => {
    // Obtain an access token — this triggers the first CIMD metadata fetch (WireMock returns V1
    // and advances the scenario state to "Changed" via newScenarioState).
    const code = await executeCimdAuthCodeFlow(fixture);

    const tokenResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        redirect_uri: fixture.redirectUri,
        client_id: fixture.clientId,
      }).toString(),
      { 'Content-Type': 'application/x-www-form-urlencoded' },
    ).expect(200);

    const accessToken: string = tokenResponse.body.access_token;
    expect(accessToken).toBeDefined();

    // Baseline: token should be active.
    const baseline = await fixture.introspectToken(accessToken);
    expect(baseline.active).toBe(true);

    // Wait for the 1-second cache TTL to expire (plus a buffer).
    await waitFor(CACHE_TTL_EXPIRY_WAIT_MS);

    // Trigger a new CIMD metadata fetch by initiating an authorization request.
    // The gateway will re-fetch the document (cache expired), receive V2 from WireMock
    // (scenario is now in "Changed" state), detect the hash difference, and asynchronously
    // revoke all tokens for this client.
    const triggerParams = new URLSearchParams({
      response_type: 'code',
      client_id: fixture.clientId,
      redirect_uri: fixture.redirectUri,
      scope: 'openid',
      state: 'cimd-revoke-trigger',
    });
    await performGet(`${fixture.oidc.authorization_endpoint}?${triggerParams.toString()}`);

    // Poll until the token becomes inactive (revocation is asynchronous).
    await retryUntil(
      () => fixture.introspectToken(accessToken).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      { timeoutMillis: 15000, intervalMillis: 300 },
    );

    const revoked = await fixture.introspectToken(accessToken);
    expect(revoked.active).toBe(false);
  });

  it('should NOT revoke an access token when the CIMD metadata document is unchanged after cache TTL expires', async () => {
    // valid-none is a static WireMock stub (no scenario state) so every fetch returns an identical
    // document. The monitored-properties hash must match across refreshes and tokens must survive.
    const stableClientId = 'http://wiremock:8080/cimd/ENABLED_BASE/valid-none';
    const stableFlowFixture = { ...fixture, clientId: stableClientId };

    const code = await executeCimdAuthCodeFlow(stableFlowFixture);
    const tokenResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        redirect_uri: fixture.redirectUri,
        client_id: stableClientId,
      }).toString(),
      { 'Content-Type': 'application/x-www-form-urlencoded' },
    ).expect(200);

    const accessToken: string = tokenResponse.body.access_token;
    expect(accessToken).toBeDefined();

    const baseline = await fixture.introspectToken(accessToken);
    expect(baseline.active).toBe(true);

    // Let the 1-second cache TTL expire, then force a fresh fetch of the (unchanged) document.
    await waitFor(CACHE_TTL_EXPIRY_WAIT_MS);
    const triggerParams = new URLSearchParams({
      response_type: 'code',
      client_id: stableClientId,
      redirect_uri: fixture.redirectUri,
      scope: 'openid',
      state: 'cimd-no-revoke-trigger',
    });
    await performGet(`${fixture.oidc.authorization_endpoint}?${triggerParams.toString()}`);

    // Continuously assert the token stays active across the observation window. This catches an
    // erroneous asynchronous revocation the moment it happens, rather than only at a single point.
    const deadline = Date.now() + NO_REVOCATION_OBSERVATION_MS;
    do {
      const probe = await fixture.introspectToken(accessToken);
      expect(probe.active).toBe(true);
      await waitFor(REVOCATION_POLL_INTERVAL_MS);
    } while (Date.now() < deadline);
  });
});
