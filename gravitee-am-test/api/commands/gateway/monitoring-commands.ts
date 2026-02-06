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

import { getMonitoringApi } from '@management-commands/service/utils';
import { DomainState } from '@gateway-apis/MonitoringApi';
import { ResponseError } from '../../management/runtime';
import { retryUntil } from '@utils-commands/retry';

const DEFAULT_TIMEOUT_MS = 30000;
const DEFAULT_INTERVAL_MS = 500;

type PollOptions = { timeoutMillis?: number; intervalMillis?: number };

let cachedApi: ReturnType<typeof getMonitoringApi> | null = null;

function createApi() {
  if (!cachedApi) {
    cachedApi = getMonitoringApi();
  }
  return cachedApi;
}

const isReady = (state: DomainState | null): boolean => state !== null && state.stable && state.synchronized;

/** Fetch domain state, returning null on transient errors instead of throwing. */
function fetchStateSafe(domainId: string): Promise<DomainState | null> {
  return getDomainState(domainId).catch((error) => {
    console.debug(`Error fetching domain state for ${domainId}: ${error instanceof Error ? error.message : error}`);
    return null;
  });
}

export const getDomainState = async (domainId: string): Promise<DomainState> => {
  return createApi().getDomainState({ domainId });
};

export const getAllDomainStates = async (): Promise<Record<string, DomainState>> => {
  return createApi().getAllDomainStates();
};

export const isDomainReady = async (domainId: string): Promise<boolean> => {
  try {
    return isReady(await getDomainState(domainId));
  } catch (error) {
    if (error instanceof ResponseError && error.response.status === 404) {
      return false;
    }
    throw error;
  }
};

/**
 * Wait for a domain to become fully ready (stable and synchronized) on the gateway.
 * Polls the _node/domains endpoint until the domain reports as ready or timeout is reached.
 */
export const waitForDomainReady = async (domainId: string, options?: PollOptions): Promise<DomainState> => {
  const { timeoutMillis = DEFAULT_TIMEOUT_MS, intervalMillis = DEFAULT_INTERVAL_MS } = options || {};
  return retryUntil(() => fetchStateSafe(domainId), isReady, {
    timeoutMillis,
    intervalMillis,
    onDone: (state) => console.debug(`Domain ${domainId} is ready (status: ${state?.status})`),
  });
};

/**
 * Wait for a domain to complete a NEW sync cycle after a change has been made.
 *
 * Unlike `waitForDomainReady` which returns immediately if the domain is already ready,
 * this captures the current `lastSync` timestamp and waits for it to advance,
 * ensuring the gateway has processed a new sync cycle (e.g. after secret renewal,
 * application update, etc.) before returning.
 */
export const waitForNextSync = async (domainId: string, options?: PollOptions): Promise<DomainState> => {
  const { timeoutMillis = DEFAULT_TIMEOUT_MS, intervalMillis = DEFAULT_INTERVAL_MS } = options || {};
  const lastSyncBefore = await fetchStateSafe(domainId).then((s) => s?.lastSync ?? 0);

  return retryUntil(
    () => fetchStateSafe(domainId),
    (state) => isReady(state) && state.lastSync > lastSyncBefore,
    {
      timeoutMillis,
      intervalMillis,
      onDone: (state) =>
      console.debug(`Domain ${domainId} synced (lastSync: ${new Date(lastSyncBefore).toISOString()} -> ${new Date(state?.lastSync).toISOString()})`),
    },
  );
};

/**
 * Snapshot the domain's lastSync, execute a mutation, then poll until lastSync advances.
 *
 * This avoids the race in `waitForNextSync` where the sync cycle completes
 * between the mutation returning and the polling starting. By capturing
 * lastSync *before* the mutation executes, we guarantee we detect the sync
 * that processes it.
 */
export const waitForSyncAfter = async <T>(domainId: string, mutation: () => Promise<T>, options?: PollOptions): Promise<T> => {
  const { timeoutMillis = DEFAULT_TIMEOUT_MS, intervalMillis = DEFAULT_INTERVAL_MS } = options || {};
  const lastSyncBefore = await fetchStateSafe(domainId).then((s) => s?.lastSync ?? 0);
  const result = await mutation();

  await retryUntil(
    () => fetchStateSafe(domainId),
    (state) => isReady(state) && state.lastSync > lastSyncBefore,
    {
      timeoutMillis,
      intervalMillis,
      onDone: (state) =>
        console.debug(`Domain ${domainId} synced after mutation (lastSync: ${new Date(lastSyncBefore).toISOString()} -> ${new Date(state?.lastSync).toISOString()})`),
    },
  );

  return result;
};
