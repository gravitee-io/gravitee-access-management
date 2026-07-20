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

import { getDomainApi, getEntrypointsApi } from '@management-commands/service/utils';
import { getDomainState, waitForDomainReady } from '@gateway-commands/monitoring-commands';
import { sendCockpitCommand } from '@cloud-commands/cockpit-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };
const DATA_PLANE_ID = process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';

const urlFor = (host: string) => `https://${host}`;

export interface CloudEntrypointFixture {
  organizationId: string;
  environmentId: string;
  domainId: string;
  initialHosts: string[];
  expectedUrls: string[];
  /** A globally-unique gateway host, safe to reuse across parallel runs. */
  uniqueHost: () => string;
  /** Re-issue the ENVIRONMENT command with a new set of gateway hosts; returns the expected https URLs. */
  resyncAccessPoints: (hosts: string[]) => Promise<string[]>;
  /** The entrypoint URLs the gateway currently has cached for this domain's environment, sorted. */
  cachedEntrypointUrls: () => Promise<string[]>;
  cleanup: () => Promise<void>;
}

/**
 * Provisions an environment via a Cockpit ENVIRONMENT command whose GATEWAY access points become
 * entrypoints, then deploys a domain in that environment so its cached entrypoints can be observed
 * through the domain state endpoint. `resyncAccessPoints` re-issues the command to exercise the live
 * update/eviction path (the handler deletes-and-recreates the environment's entrypoints each time).
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudEntrypointFixture = async (accessToken: string): Promise<CloudEntrypointFixture> => {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = uniqueName('env-ep', true);
  const uniqueHost = () => `${uniqueName('gw', true)}.example.com`;

  // Track every host we ever provision so cleanup removes all of them, not just the current set
  // (each ENVIRONMENT command deletes-and-recreates the environment's entrypoints).
  const provisionedHosts = new Set<string>();
  const resyncAccessPoints = async (hosts: string[]): Promise<string[]> => {
    hosts.forEach((host) => provisionedHosts.add(host));
    await sendCockpitCommand({
      type: 'ENVIRONMENT',
      payload: {
        id: environmentId,
        organizationId,
        hrids: [environmentId],
        name: 'AM7226 cloud entrypoint env',
        accessPoints: hosts.map((host) => ({ target: 'GATEWAY', host })),
      },
    });
    return hosts.map(urlFor);
  };

  // 1. Cockpit provisions the environment; its GATEWAY access points are turned into entrypoints.
  const initialHosts = [uniqueHost(), uniqueHost()];
  const expectedUrls = await resyncAccessPoints(initialHosts);

  // 2. Wait until AM has processed the command (environment created + entrypoints persisted).
  await retryUntil(
    () => getEntrypointsApi(accessToken).listEntrypoints({ organizationId }),
    (entrypoints: any[]) => expectedUrls.every((url) => entrypoints.some((e) => e.url === url)),
    POLL,
  );

  // 3. Deploy an enabled domain in that environment so its cached entrypoints surface on domain state.
  const domainApi = getDomainApi(accessToken);
  const domain = await domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: { name: uniqueName('ep-cache-domain', true), dataPlaneId: DATA_PLANE_ID },
  });
  await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id, patchDomain: { enabled: true } });
  await waitForDomainReady(domain.id);

  const cachedEntrypointUrls = async (): Promise<string[]> => {
    const state = await getDomainState(domain.id);
    return (state.entrypoints ?? []).map((e) => e.url).sort();
  };

  const cleanup = async () => {
    await domainApi
      .deleteDomain({ organizationId, environmentId, domain: domain.id })
      .catch((e) => console.warn(`cleanup: failed to delete domain ${domain.id}: ${e.message}`));
    const provisionedUrls = [...provisionedHosts].map(urlFor);
    const entrypoints = await getEntrypointsApi(accessToken)
      .listEntrypoints({ organizationId })
      .catch((e) => {
        console.warn(`cleanup: failed to list entrypoints: ${e.message}`);
        return [] as any[];
      });
    await Promise.all(
      entrypoints
        .filter((e: any) => provisionedUrls.includes(e.url))
        .map((e: any) =>
          getEntrypointsApi(accessToken)
            .deleteEntrypoint({ organizationId, entrypointId: e.id })
            .catch((err) => console.warn(`cleanup: failed to delete entrypoint ${e.id}: ${err.message}`)),
        ),
    );
  };

  return {
    organizationId,
    environmentId,
    domainId: domain.id,
    initialHosts,
    expectedUrls,
    uniqueHost,
    resyncAccessPoints,
    cachedEntrypointUrls,
    cleanup,
  };
};
