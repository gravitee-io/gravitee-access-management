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

import { getDomainApi, getDomainManagerUrl } from './service/utils';
import { Domain } from '../../management/models';
import { DomainPage } from '../../management/models/DomainPage';
import { retryUntil } from '@utils-commands/retry';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { expect } from '@jest/globals';
import faker from 'faker';

import request from 'supertest';

export type DomainOidcConfig = {
  authorization_endpoint: string;
  userinfo_endpoint: string;
  token_endpoint: string;
  end_session_endpoint: string;
  introspection_endpoint: string;
  [key: string]: any;
};
export type DomainWithOidcConfig = { domain: Domain; oidcConfig?: DomainOidcConfig };
export type DomainListOptions = {
  page?: number;
  size?: number;
  q?: string;
};

export const setupDomainForTest = async (
  domainName: string,
  options?: { accessToken?: string; waitForStart?: boolean },
): Promise<DomainWithOidcConfig> => {
  const token = options.accessToken ?? (await requestAdminAccessToken());
  expect(token).toBeDefined();

  const createdDomain = await createDomain(token, domainName, faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, token);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);
  if (options.waitForStart === true) {
    return waitForDomainStart(createdDomain);
  } else {
    return { domain: domainStarted, oidcConfig: undefined };
  }
};

export const createDomain = (accessToken, name, description): Promise<Domain> =>
  getDomainApi(accessToken).createDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    newDomain: {
      name: name,
      description: description,
      dataPlaneId: 'default',
    },
  });

export const deleteDomain = (domainId, accessToken): Promise<void> =>
  getDomainApi(accessToken).deleteDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  });

/**
 * Safely deletes a domain, handling common errors gracefully.
 * Logs success/failure but never throws - ensures cleanup continues.
 * Use this in afterAll() blocks to ensure all domains are cleaned up even if one fails.
 *
 * @param domainId - The domain ID to delete
 * @param accessToken - Admin access token
 * @returns Promise<void> - Always resolves (never rejects)
 */
export const safeDeleteDomain = async (domainId: string | null | undefined, accessToken: string | null | undefined): Promise<void> => {
  if (!domainId || !accessToken) {
    console.warn('⚠️  Cannot delete domain: domainId or accessToken is missing');
    return;
  }

  try {
    await deleteDomain(domainId, accessToken);
    console.log(`✅ Deleted domain: ${domainId}`);
  } catch (err: any) {
    if (err.response?.status === 404) {
      console.log(`ℹ️  Domain already deleted: ${domainId}`);
    } else if (err.response?.status) {
      console.warn(`⚠️  Failed to delete domain ${domainId} - HTTP ${err.response.status}: ${err.message}`);
    } else {
      console.warn(`⚠️  Failed to delete domain ${domainId}: ${err.message}`);
    }
    // Never throw - allow cleanup to continue
  }
};

export const patchDomain = (domainId, accessToken, body): Promise<Domain> =>
  getDomainApi(accessToken).patchDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
    // domain payload
    patchDomain: body,
  });

export const startDomain = (domainId: string, accessToken): Promise<Domain> => patchDomain(domainId, accessToken, { enabled: true });

export const getDomain = (domainId, accessToken): Promise<Domain> =>
  getDomainApi(accessToken).findDomain({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
  });

export const listDomains = (accessToken: string, options?: DomainListOptions): Promise<DomainPage> =>
  getDomainApi(accessToken).listDomains({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    page: options?.page,
    size: options?.size,
    q: options?.q,
  });

export const createAcceptAllDeviceNotifier = (domainId, accessToken) =>
  request(getDomainManagerUrl(null) + '/auth-device-notifiers')
    .post('')
    .set('Authorization', 'Bearer ' + accessToken)
    .send({
      type: 'http-am-authdevice-notifier',
      configuration:
        '{"endpoint":"http://localhost:8080/ciba/notify/accept-all","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}',
      name: 'Always OK notifier',
    })
    .expect(201);

export const getDomainFlows = (domainId, accessToken) =>
  getDomainApi(accessToken).listDomainFlows({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
  });

export const updateDomainFlows = (domainId, accessToken, flows) =>
  getDomainApi(accessToken).defineDomainFlows({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    // domain in path param
    domain: domainId,
    flow: flows,
  });

export const waitForDomainStart: (domain: Domain) => Promise<DomainWithOidcConfig> = (domain: Domain) => {
  const start = Date.now();
  return retryUntil(
    () => getWellKnownOpenIdConfiguration(domain.hrid) as Promise<any>,
    (res) => res.status == 200,
    {
      timeoutMillis: 5000,
      onDone: () => console.log(`domain "${domain.hrid}" ready after ${(Date.now() - start) / 1000}s`),
      onRetry: () => console.debug(`domain "${domain.hrid}" not ready yet`),
    },
  ).then((response) => ({ domain, oidcConfig: response.body }));
};

/**
 * Wait for domain sync to complete.
 *
 * This function polls the domain's updatedAt timestamp to detect when sync is complete.
 * Sync is considered complete when the domain's updatedAt timestamp has been stable
 * for a short period (indicating no new updates are in progress).
 *
 * Uses the existing `retryUntil` utility for consistency with other wait operations.
 *
 * Note: This is a heuristic approach - without a direct sync status API, we infer sync
 * completion by checking if the domain's updatedAt timestamp has been stable. This works
 * because domain updates trigger sync operations, and once updatedAt stabilizes, sync is
 * typically complete.
 *
 * @param domainId - Optional domain ID to poll. If not provided, uses a shorter fixed wait.
 * @param accessToken - Optional access token for polling. Required if domainId is provided.
 * @param options - Optional configuration:
 *   - timeoutMillis: Maximum time to wait (default: 30000ms)
 *   - intervalMillis: Polling interval (default: 500ms)
 *   - stabilityMillis: Time domain must be stable before considering sync complete (default: 2000ms)
 *
 * @returns Promise that resolves when sync is complete
 */
// Constants for domain sync timing
const DEFAULT_DOMAIN_SYNC_TIMEOUT_MS = 30000;
const DEFAULT_DOMAIN_SYNC_INTERVAL_MS = 500;
const DEFAULT_DOMAIN_SYNC_STABILITY_MS = 2000;
const DOMAIN_READY_MIN_WAIT_MS = 500;
const DOMAIN_SYNC_FALLBACK_WAIT_MS = 2000;

export const waitForDomainSync = async (
  domainId?: string,
  accessToken?: string,
  options?: {
    timeoutMillis?: number;
    intervalMillis?: number;
    stabilityMillis?: number;
  },
): Promise<void> => {
  const {
    timeoutMillis = DEFAULT_DOMAIN_SYNC_TIMEOUT_MS,
    intervalMillis = DEFAULT_DOMAIN_SYNC_INTERVAL_MS,
    stabilityMillis = DEFAULT_DOMAIN_SYNC_STABILITY_MS,
  } = options || {};

  // Early return for fallback case (no domainId or accessToken)
  if (!domainId || !accessToken) {
    // Fallback: Use shorter fixed wait (2 seconds instead of 10)
    // This is a conservative improvement that maintains backward compatibility
    // while still providing significant time savings
    await waitFor(DOMAIN_SYNC_FALLBACK_WAIT_MS);
    return;
  }

  // Use polling with retryUntil when domainId and accessToken are provided
  // State tracking for stability check
  const state = {
    lastUpdatedAt: null as number | null,
    stableSince: null as number | null,
  };

  try {
    await retryUntil(
      async () => {
        try {
          const domain = await getDomain(domainId, accessToken);
          const currentUpdatedAt = domain.updatedAt ? new Date(domain.updatedAt).getTime() : null;
          return { updatedAt: currentUpdatedAt, timestamp: Date.now() };
        } catch (error: unknown) {
          // If domain fetch fails, return null as fallback
          // This allows retry logic to continue (domain might not be ready yet)
          // Log at debug level to handle the exception while avoiding log spam
          const errorMessage = error instanceof Error ? error.message : String(error);
          console.debug(`Error fetching domain ${domainId} for sync check: ${errorMessage}`);
          return { updatedAt: null, timestamp: Date.now() };
        }
      },
      (result) => {
        const { updatedAt, timestamp } = result;

        // If updatedAt is null, domain is not ready or has no timestamp. Continue polling.
        if (updatedAt === null) {
          return false;
        }

        // First check - initialize state
        if (state.lastUpdatedAt === null) {
          state.lastUpdatedAt = updatedAt;
          state.stableSince = timestamp;
          return false;
        }

        // Domain was updated - reset stability timer
        if (updatedAt !== state.lastUpdatedAt) {
          state.lastUpdatedAt = updatedAt;
          state.stableSince = timestamp;
          return false;
        }

        // Domain hasn't been updated - check if stable long enough
        const stableDuration = timestamp - (state.stableSince || timestamp);
        return stableDuration >= stabilityMillis;
      },
      {
        timeoutMillis,
        intervalMillis,
        onDone: () => {
          const duration = state.stableSince ? Date.now() - (state.stableSince || 0) : 0;
          console.debug(`Domain ${domainId} sync complete (stable for ${duration}ms)`);
        },
        onRetry: () => {
          // Silent retry - avoid log spam
        },
      },
    );

    // Additional minimum wait to ensure domain is ready to serve requests
    // Stability check doesn't guarantee domain is ready to handle requests
    await waitFor(DOMAIN_READY_MIN_WAIT_MS);
  } catch (error: unknown) {
    // Timeout or error - log warning but don't throw (backward compatibility)
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.warn(`Domain ${domainId} sync timeout after ${timeoutMillis}ms: ${errorMessage}`);
  }
};

export async function waitForOidcReady(
  domainHrid: string,
  options?: {
    timeoutMs?: number;
    intervalMs?: number;
  },
) {
  const { timeoutMs = DEFAULT_DOMAIN_SYNC_TIMEOUT_MS, intervalMs = DEFAULT_DOMAIN_SYNC_INTERVAL_MS } = options || {};

  const start = Date.now();
  let lastStatus: number | undefined;
  let lastError: unknown;

  while (Date.now() - start < timeoutMs) {
    try {
      const res = await getWellKnownOpenIdConfiguration(domainHrid);

      lastStatus = res.status;

      if (res.status === 200 && res.body) {
        return res;
      }
    } catch (err) {
      lastError = err;
    }

    await waitFor(DOMAIN_READY_MIN_WAIT_MS);
  }

  throw new Error(
    `Timed out waiting for OIDC config for domain "${domainHrid}" after ${timeoutMs}ms. ` +
      (lastStatus ? `Last status: ${lastStatus}` : '') +
      (lastError instanceof Error ? ` Last error: ${lastError.message}` : ''),
  );
}

export const waitFor = (duration) => new Promise((r) => setTimeout(r, duration));

export async function allowHttpLocalhostRedirects(domain: Domain, accessToken: string) {
  return patchDomain(domain.id, accessToken, {
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
      },
    },
  });
}
