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
import { waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { CloudOrganizationFixture, setupCloudOrganizationFixture } from './fixtures/cloud-organization-fixture';
import { getDomainApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../test-fixture';
import { CloudEntrypointFixture, setupCloudEntrypointFixture } from './fixtures/cloud-entrypoint-fixture';

setup(120000);

const ORGANIZATION_NAME = 'cloud-org-domain-ep';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };

let organization: CloudOrganizationFixture;
let fixture: CloudEntrypointFixture;

beforeAll(async () => {
  await waitForCockpitConnection();
  organization = await setupCloudOrganizationFixture(ORGANIZATION_NAME);
  fixture = await setupCloudEntrypointFixture(organization);
});

afterAll(async () => {
  await fixture?.cleanup();
  await organization?.cleanup();
});

// The urls the management API resolves for the domain's Overview/Endpoints pages
// (GET /organizations/{org}/environments/{env}/domains/{domain}/entrypoints).
const domainEntrypointUrls = async (): Promise<string[]> => {
  const entrypoints = await getDomainApi(organization.accessToken).getDomainEntrypoints({
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
  it('resolves every access point when none of them is the customer override', async () => {
    const urls = await retryUntil(
      () => domainEntrypointUrls(),
      (resolved) => resolved.length === fixture.expectedUrls.length,
      POLL,
    );

    expect([...urls].sort()).toEqual([...fixture.expectedUrls].sort());
  });

  it("resolves the customer's overriding access point and drops the one Cockpit generated", async () => {
    const generatedHost = fixture.uniqueHost();
    const overridingHost = fixture.uniqueHost();
    await fixture.resyncAccessPoints([
      { host: generatedHost, overriding: false },
      { host: overridingHost, overriding: true },
    ]);

    const urls = await retryUntil(
      () => domainEntrypointUrls(),
      (resolved) => resolved.length === 1 && resolved[0] === `https://${overridingHost}`,
      POLL,
    );

    expect(urls).toEqual([`https://${overridingHost}`]);
    expect(urls).not.toContain(`https://${generatedHost}`);
  });

  it('resolves every overriding access point when the customer has several custom domains', async () => {
    const first = fixture.uniqueHost();
    const second = fixture.uniqueHost();
    await fixture.resyncAccessPoints([
      { host: first, overriding: true },
      { host: second, overriding: true },
    ]);

    const expected = [`https://${first}`, `https://${second}`].sort();
    const urls = await retryUntil(
      () => domainEntrypointUrls(),
      (resolved) => resolved.length === 2,
      POLL,
    );

    expect([...urls].sort()).toEqual(expected);
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

  it('falls back to an organization entrypoint when the environment has no access points', async () => {
    await fixture.resyncAccessPoints([]);

    // Once the environment entrypoint is evicted, the endpoint degrades to the organization
    // resolution: still exactly one entrypoint (never an empty list — that would crash the UI),
    // carrying the DataPlane gatewayUrl or the org default entrypoint's url. Either way the url
    // is a real URL, never null (the UI builds links from it), and no longer an environment host.
    const urls = await retryUntil(
      () => domainEntrypointUrls(),
      (resolved) => resolved.length === 1 && resolved[0] != null && !resolved[0].endsWith('.example.com'),
      POLL,
    );

    expect(urls).toHaveLength(1);
    expect(urls[0]).toMatch(/^https?:\/\//);
  });
});
