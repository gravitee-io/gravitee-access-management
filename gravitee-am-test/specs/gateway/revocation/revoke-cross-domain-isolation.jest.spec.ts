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
import { patchApplication } from '@management-commands/application-management-commands';
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import { updateUserStatus } from '@management-commands/user-management-commands';
import { setup } from '../../test-fixture';
import { setupRevocationFixture, waitPastOfflineVerification, RevocationFixture } from './fixtures/revocation-fixture';

setup(300000);

/**
 * Tenancy guard for revocation. Revoke events carry the originating domain id and the gateway
 * listener filters on it (RevokeTokenGatewayServiceImpl#onEvent), while the repository deletes are
 * themselves domain-scoped — so a leak needs two independent failures. This is defence in depth
 * rather than a suspected bug, but multi-tenant token loss is severe enough to be worth pinning.
 *
 * The BY_CLIENT case deliberately gives both domains an application with the *same* clientId,
 * since clientId uniqueness in AM is per-domain. That is the shape a scoping regression would
 * actually take: `deleteByDomainIdAndClientId` losing its domain predicate.
 */
const SHARED_CLIENT_NAME = 'revoke-isolation-shared-client';

let domainA: RevocationFixture;
let domainB: RevocationFixture;

beforeAll(async () => {
  domainA = await setupRevocationFixture({ domainNamePrefix: 'revoke-isolation-a', enableTokenExchange: false });
  domainB = await setupRevocationFixture({ domainNamePrefix: 'revoke-isolation-b', enableTokenExchange: false });
});

afterAll(async () => {
  if (domainA) {
    await domainA.cleanup();
  }
  if (domainB) {
    await domainB.cleanup();
  }
});

describe('Revocation isolation - across domains', () => {
  it('should not revoke tokens in another domain when a user is disabled', async () => {
    const tokensA = await domainA.obtainAuthorizationCodeTokens();
    const tokensB = await domainB.obtainAuthorizationCodeTokens();

    expect((await domainA.getUserInfo(tokensA.accessToken)).status).toBe(200);
    expect((await domainB.getUserInfo(tokensB.accessToken)).status).toBe(200);

    await updateUserStatus(domainA.domain.id, domainA.accessToken, domainA.user.id, false);
    await updateUserStatus(domainA.domain.id, domainA.accessToken, domainA.user.id, true);

    await waitPastOfflineVerification(tokensA.accessToken);
    await waitPastOfflineVerification(tokensB.accessToken);

    expect((await domainA.getUserInfo(tokensA.accessToken)).status).toBe(401);
    expect((await domainB.getUserInfo(tokensB.accessToken)).status).toBe(200);
  });

  it('should not revoke tokens for an identically named client in another domain', async () => {
    const clientA = await domainA.createAdditionalClient(SHARED_CLIENT_NAME, { exactName: true });
    const clientB = await domainB.createAdditionalClient(SHARED_CLIENT_NAME, { exactName: true });

    // The premise of the test: one clientId, two domains.
    expect(clientB.application.settings.oauth.clientId).toEqual(clientA.application.settings.oauth.clientId);

    const tokensA = await clientA.obtainAuthorizationCodeTokens();
    const tokensB = await clientB.obtainAuthorizationCodeTokens();

    expect((await domainA.getUserInfo(tokensA.accessToken)).status).toBe(200);
    expect((await domainB.getUserInfo(tokensB.accessToken)).status).toBe(200);

    await patchApplication(domainA.domain.id, domainA.accessToken, { enabled: false }, clientA.application.id);
    await waitForOidcReady(domainA.domain.hrid, { timeoutMs: 5000, intervalMs: 200 });

    // No explicit sync wait: the offline-verification wait below is an order of magnitude longer
    // than a gateway sync cycle, so the disable has certainly propagated by the time we assert.
    await waitPastOfflineVerification(tokensA.accessToken);
    await waitPastOfflineVerification(tokensB.accessToken);

    expect((await domainA.getUserInfo(tokensA.accessToken)).status).toBe(401);
    expect((await domainB.getUserInfo(tokensB.accessToken)).status).toBe(200);
  });
});
