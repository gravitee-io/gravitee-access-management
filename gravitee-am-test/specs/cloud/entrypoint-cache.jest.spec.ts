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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { getDomainState } from '@gateway-commands/monitoring-commands';
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../test-fixture';
import { CloudEntrypointFixture, setupCloudEntrypointFixture } from './fixtures/cloud-entrypoint-fixture';

setup(120000);

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };
const sameUrls = (urls: string[], expected: string[]) => urls.length === expected.length && expected.every((u) => urls.includes(u));

let accessToken: string;
let fixture: CloudEntrypointFixture;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  await waitForCockpitConnection();
  fixture = await setupCloudEntrypointFixture(accessToken);
});

afterAll(async () => {
  await fixture?.cleanup();
});

// These specs share one environment/domain and run in order (jest --runInBand): the initial access
// points are asserted first, then re-synced to a new set, then cleared. Each ENVIRONMENT command
// deletes-and-recreates the environment's entrypoints, and the gateway cache refreshes off the sync
// event without redeploying the domain.
describe('Cloud entrypoint cache (Cockpit access points -> gateway)', () => {
  it("surfaces the environment's Cockpit access points as cached entrypoints on the domain state", async () => {
    const state = await retryUntil(
      () => getDomainState(fixture.domainId),
      (s) =>
        (s.entrypoints ?? []).length === fixture.expectedUrls.length &&
        fixture.expectedUrls.every((url) => (s.entrypoints ?? []).some((e) => e.url === url)),
      POLL,
    );

    const cached = state.entrypoints ?? [];
    expect(cached.map((e) => e.url).sort()).toEqual([...fixture.expectedUrls].sort());
    cached.forEach((entrypoint) => {
      expect(entrypoint.environmentId).toBe(fixture.environmentId);
      expect(entrypoint.organizationId).toBe(fixture.organizationId);
    });
  });

  it('refreshes the cached entrypoints when the environment access points change', async () => {
    const keptHost = fixture.initialHosts[1];
    const droppedUrl = `https://${fixture.initialHosts[0]}`;
    const addedHost = fixture.uniqueHost();
    const expectedUrls = await fixture.resyncAccessPoints([keptHost, addedHost]);

    const cached = await retryUntil(() => fixture.cachedEntrypointUrls(), (urls) => sameUrls(urls, expectedUrls), POLL);

    expect(cached).toEqual([...expectedUrls].sort());
    expect(cached).not.toContain(droppedUrl);
  });

  it('evicts every cached entrypoint when the environment has no gateway access points', async () => {
    await fixture.resyncAccessPoints([]);

    const cached = await retryUntil(() => fixture.cachedEntrypointUrls(), (urls) => urls.length === 0, POLL);

    expect(cached).toEqual([]);
  });
});
