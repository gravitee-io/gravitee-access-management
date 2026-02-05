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

/**
 * Creates a MonitoringApi instance that handles 503 responses gracefully.
 * The _node/domains endpoint returns 503 with a valid JSON body when domains aren't ready,
 * so we use post-middleware to treat 503 as a successful response for parsing purposes.
 */
function createApi() {
  return getMonitoringApi().withPostMiddleware(async ({ response }) => {
    if (response.status === 503) {
      const body = await response.text();
      return new Response(body, {
        status: 200,
        statusText: 'OK',
        headers: response.headers,
      });
    }
    return response;
  });
}

/**
 * Get the readiness state of a specific domain from the gateway's _node/domains endpoint.
 * Returns the full DomainState regardless of whether the domain is ready or not.
 *
 * @param domainId - The domain ID to check
 * @returns DomainState with sync status, plugin creation states, and readiness flags
 * @throws ResponseError if the domain is not found (404) or other unexpected errors
 */
export const getDomainState = async (domainId: string): Promise<DomainState> => {
  return createApi().getDomainState({ domainId });
};

/**
 * Get the readiness state of all domains from the gateway's _node/domains endpoint.
 *
 * @returns Map of domain ID to DomainState
 */
export const getAllDomainStates = async (): Promise<Record<string, DomainState>> => {
  return createApi().getAllDomainStates();
};

/**
 * Check if a specific domain is fully ready (stable and synchronized).
 *
 * @param domainId - The domain ID to check
 * @returns true if the domain is deployed, all plugins loaded successfully, and sync is complete
 */
export const isDomainReady = async (domainId: string): Promise<boolean> => {
  try {
    const state = await getDomainState(domainId);
    return state.stable && state.synchronized;
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
 *
 * @param domainId - The domain ID to wait for
 * @param options - Polling configuration
 * @returns The final DomainState once ready
 * @throws TimeoutError if the domain does not become ready within the timeout
 */
export const waitForDomainReady = async (
  domainId: string,
  options?: {
    timeoutMillis?: number;
    intervalMillis?: number;
  },
): Promise<DomainState> => {
  const { timeoutMillis = DEFAULT_TIMEOUT_MS, intervalMillis = DEFAULT_INTERVAL_MS } = options || {};

  return retryUntil(
    async () => {
      try {
        return await getDomainState(domainId);
      } catch (error: unknown) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        console.debug(`Error fetching domain state for ${domainId}: ${errorMessage}`);
        return null;
      }
    },
    (state) => state !== null && state.stable && state.synchronized,
    {
      timeoutMillis,
      intervalMillis,
      onDone: (state) => console.debug(`Domain ${domainId} is ready (status: ${state?.status})`),
      onRetry: (state) => {
        if (state) {
          console.debug(`Domain ${domainId} not ready (status: ${state.status}, stable: ${state.stable}, synchronized: ${state.synchronized})`);
        }
      },
    },
  );
};
