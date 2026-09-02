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
import { bindSafeDeleteCloudDomain } from '@cloud-commands/domain-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { setupCloudSharedFixture } from './cloud-shared-fixture';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };

const urlFor = (host: string) => `https://${host}`;

/**
 * A gateway access point to provision. A bare host is the access point Cockpit generates itself
 * (`overriding` absent, exactly as older Cockpit versions send it); the object form sets the flag
 * explicitly so specs can provision a customer's overriding access point.
 */
export type AccessPointSpec = string | { host: string; overriding: boolean };

const hostOf = (spec: AccessPointSpec): string => (typeof spec === 'string' ? spec : spec.host);

const accessPointPayload = (spec: AccessPointSpec) =>
  typeof spec === 'string' ? { target: 'GATEWAY', host: spec } : { target: 'GATEWAY', host: spec.host, overriding: spec.overriding };

export interface CloudEntrypointFixture {
  organizationId: string;
  environmentId: string;
  domainId: string;
  initialHosts: string[];
  expectedUrls: string[];
  /** A globally-unique gateway host, safe to reuse across parallel runs. */
  uniqueHost: () => string;
  /** Re-issue the ENVIRONMENT command with a new set of gateway access points; returns the expected https URLs. */
  resyncAccessPoints: (accessPoints: AccessPointSpec[]) => Promise<string[]>;
  /** The entrypoint URLs the gateway currently has cached for this domain's environment, sorted. */
  cachedEntrypointUrls: () => Promise<string[]>;
  /** The entrypoint URLs the management API stores for this environment, sorted. */
  storedEntrypointUrls: () => Promise<string[]>;
  cleanup: () => Promise<void>;
}

/**
 * Deploys an enabled domain in the shared cloud environment so its cached entrypoints can be
 * observed through the domain state endpoint. `resyncAccessPoints` re-issues the ENVIRONMENT
 * command to exercise the live update/eviction path.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudEntrypointFixture = async (): Promise<CloudEntrypointFixture> => {
  const shared = await setupCloudSharedFixture();
  const { organizationId, environmentId, accessToken, dataPlaneId } = shared;
  const uniqueHost = () => `${uniqueName('gw', true)}.example.com`;

  const resyncAccessPoints = async (accessPoints: AccessPointSpec[]): Promise<string[]> => {
    const hosts = accessPoints.map(hostOf);
    await sendCockpitCommand({
      type: 'ENVIRONMENT',
      payload: {
        id: environmentId,
        organizationId,
        hrids: [environmentId],
        name: 'AM7226 cloud entrypoint env',
        accessPoints: accessPoints.map(accessPointPayload),
      },
    });
    return hosts.map(urlFor);
  };

  // 1. Set this spec's access points on the shared env.
  const initialHosts = [uniqueHost(), uniqueHost()];
  const expectedUrls = await resyncAccessPoints(initialHosts);

  // 2. Wait until AM has processed the command (entrypoints persisted).
  await retryUntil(
    () => getEntrypointsApi(accessToken).listEntrypoints({ organizationId }),
    (entrypoints: any[]) => expectedUrls.every((url) => entrypoints.some((e) => e.url === url)),
    POLL,
  );

  // 3. Deploy an enabled domain so its cached entrypoints surface on domain state.
  const domainApi = getDomainApi(accessToken);
  const domain = await domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: { name: uniqueName('ep-cache-domain', true), dataPlaneId },
  });
  await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id, patchDomain: { enabled: true } });
  await waitForDomainReady(domain.id);

  const cachedEntrypointUrls = async (): Promise<string[]> => {
    const state = await getDomainState(domain.id);
    return (state.entrypoints ?? []).map((e) => e.url).sort();
  };

  const storedEntrypointUrls = async (): Promise<string[]> => {
    const entrypoints = await getEntrypointsApi(accessToken).listEntrypoints({ organizationId });
    return entrypoints
      .filter((e: any) => e.environmentId === environmentId)
      .map((e: any) => e.url)
      .sort();
  };

  const deleteDomain = bindSafeDeleteCloudDomain({ accessToken, organizationId, environmentId });

  const cleanup = async () => {
    await deleteDomain(domain.id);
    try {
      await shared.restoreAccessPoints();
    } catch (err: any) {
      console.warn(`cleanup: failed to restore the shared environment's access points: ${err.message}`);
    }
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
    storedEntrypointUrls,
    cleanup,
  };
};
