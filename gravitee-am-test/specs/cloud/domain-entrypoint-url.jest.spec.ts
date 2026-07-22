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
import { getDomainApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../test-fixture';
import { CloudEntrypointFixture, setupCloudEntrypointFixture } from './fixtures/cloud-entrypoint-fixture';

setup(120000);

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };

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

// The urls the management API resolves for the domain's Overview/Endpoints pages
// (GET /organizations/{org}/environments/{env}/domains/{domain}/entrypoints).
const domainEntrypointUrls = async (): Promise<string[]> => {
  const entrypoints = await getDomainApi(accessToken).getDomainEntrypoints({
    organizationId: fixture.organizationId,
    environmentId: fixture.environmentId,
    domain: fixture.domainId,
  });
  return (entrypoints ?? []).map((e: any) => e.url);
};

// These specs share one environment/domain and run in order (jest --runInBand): several access points
// first, then re-synced to a single one. In cloud mode the endpoint resolves the environment entrypoint,
// not the internal data-plane gateway URL. retryUntil covers the management API sync-tick cache latency.
describe('Cloud domain entrypoint URL (management API)', () => {
  it('resolves a single environment access-point URL when the environment has several access points', async () => {
    // The fixture provisions two GATEWAY access points; cloud mode returns exactly one (the
    // default-flagged access point if any, otherwise the first), always one of the environment
    // URLs (never the gateway fallback).
    const urls = await retryUntil(
      () => domainEntrypointUrls(),
      (resolved) => resolved.length === 1 && fixture.expectedUrls.includes(resolved[0]),
      POLL,
    );

    expect(urls).toHaveLength(1);
    expect(fixture.expectedUrls).toContain(urls[0]);
  });

  it('resolves the environment access-point URL when the environment has a single access point', async () => {
    const [expectedUrl] = await fixture.resyncAccessPoints([fixture.uniqueHost()]);

    const urls = await retryUntil(
      () => domainEntrypointUrls(),
      (resolved) => resolved.length === 1 && resolved[0] === expectedUrl,
      POLL,
    );

    expect(urls).toEqual([expectedUrl]);
  });

  it('never returns an empty list when the environment has no access points', async () => {
    await fixture.resyncAccessPoints([]);

    // Once the environment entrypoint is evicted, the endpoint must still return exactly one
    // entrypoint (the fallback), never an empty list — an empty list would crash the UI. In managed
    // cloud the DataPlane gatewayUrl may be absent, so the fallback entrypoint can have no url; the
    // guarantee under test is that the list stays non-empty and is no longer an environment host.
    const urls = await retryUntil(
      () => domainEntrypointUrls(),
      (resolved) => resolved.length === 1 && (resolved[0] == null || !resolved[0].endsWith('.example.com')),
      POLL,
    );

    expect(urls).toHaveLength(1);
  });
});
