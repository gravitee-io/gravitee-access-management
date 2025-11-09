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
import { retryUntil } from '@utils-commands/retry';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { expect } from '@jest/globals';
import faker from 'faker';
import { getApplication } from './application-management-commands';

const request = require('supertest');

export type DomainOidcConfig = {
  userinfo_endpoint: string;
  token_endpoint: string;
  end_session_endpoint: string;
  introspection_endpoint: string;
  [key: string]: any;
};
export type DomainWithOidcConfig = { domain: Domain; oidcConfig?: DomainOidcConfig };

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
export const safeDeleteDomain = async (domainId: string, accessToken: string): Promise<void> => {
  if (!domainId) {
    console.warn('⚠️  Cannot delete domain: domainId is undefined or empty');
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
      timeoutMillis: 10000,
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
export const waitForDomainSync = async (
  domainId?: string,
  accessToken?: string,
  options?: {
    timeoutMillis?: number;
    intervalMillis?: number;
    stabilityMillis?: number;
  }
): Promise<void> => {
  const {
    timeoutMillis = 30000,
    intervalMillis = 500,
    stabilityMillis = 2000,
  } = options || {};

  // If domainId and accessToken are provided, use polling with retryUntil
  if (domainId && accessToken) {
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
            const currentUpdatedAt = domain.updatedAt ? new Date(domain.updatedAt).getTime() : Date.now();
            return { updatedAt: currentUpdatedAt, timestamp: Date.now() };
          } catch (error: any) {
            // If domain fetch fails, return current time as fallback
            // This allows retry logic to continue (domain might not be ready yet)
            // Log at debug level to handle the exception while avoiding log spam
            console.debug(`Error fetching domain ${domainId} for sync check: ${error.message}`);
            return { updatedAt: Date.now(), timestamp: Date.now() };
          }
        },
        (result) => {
          const { updatedAt, timestamp } = result;

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
        }
      );
      
      // Additional minimum wait to ensure domain is ready to serve requests
      // Stability check doesn't guarantee domain is ready to handle requests
      await waitFor(500);
    } catch (error: any) {
      // Timeout or error - log warning but don't throw (backward compatibility)
      console.warn(`Domain ${domainId} sync timeout after ${timeoutMillis}ms: ${error.message}`);
    }
    return;
  }

  // Fallback: Use shorter fixed wait (2 seconds instead of 10)
  // This is a conservative improvement that maintains backward compatibility
  // while still providing significant time savings
  await waitFor(2000);
};

export const waitFor = (duration) => new Promise((r) => setTimeout(r, duration));

/**
 * Wait for application sync to complete after an update.
 * 
 * This function ensures that application changes have been synced to the gateway.
 * It works by:
 * 1. Waiting for domain sync to complete (domain updatedAt is stable)
 * 2. Verifying that the application's updatedAt timestamp is stable
 * 3. Adding an additional delay to allow ApplicationEvent to be processed by ClientManagerImpl
 * 
 * This helps avoid race conditions where domain sync completes but the ApplicationEvent
 * hasn't been processed yet, causing the Client object in the gateway to have stale data.
 * 
 * @param domainId - Domain ID
 * @param accessToken - Access token for API calls
 * @param applicationId - Application ID to verify sync
 * @param options - Optional configuration:
 *   - timeoutMillis: Maximum time to wait (default: 30000ms)
 *   - intervalMillis: Polling interval (default: 500ms)
 *   - stabilityMillis: Time application must be stable before considering sync complete (default: 2000ms)
 *   - eventProcessingDelay: Additional delay to allow events to process (default: 1000ms)
 * 
 * @returns Promise that resolves when application sync is complete
 */
export const waitForApplicationSync = async (
  domainId: string,
  accessToken: string,
  applicationId: string,
  options?: {
    timeoutMillis?: number;
    intervalMillis?: number;
    stabilityMillis?: number;
    eventProcessingDelay?: number;
  }
): Promise<void> => {
  const {
    timeoutMillis = 30000,
    intervalMillis = 500,
    stabilityMillis = 2000,
    eventProcessingDelay = 1000,
  } = options || {};

  // First, wait for domain sync to complete
  await waitForDomainSync(domainId, accessToken, {
    timeoutMillis: timeoutMillis * 0.7, // Use 70% of timeout for domain sync
    intervalMillis,
    stabilityMillis,
  });

  // Wait for application's updatedAt to be stable
  const state = {
    lastUpdatedAt: null as number | null,
    stableSince: null as number | null,
  };

  try {
    await retryUntil(
      async () => {
        try {
          const app = await getApplication(domainId, accessToken, applicationId);
          const currentUpdatedAt = app.updatedAt ? new Date(app.updatedAt).getTime() : Date.now();
          return { updatedAt: currentUpdatedAt, timestamp: Date.now() };
        } catch (error: any) {
          console.debug(`Error fetching application ${applicationId} for sync check: ${error.message}`);
          return { updatedAt: Date.now(), timestamp: Date.now() };
        }
      },
      (result) => {
        const { updatedAt, timestamp } = result;

        // First check - initialize state
        if (state.lastUpdatedAt === null) {
          state.lastUpdatedAt = updatedAt;
          state.stableSince = timestamp;
          return false;
        }

        // Application was updated - reset stability timer
        if (updatedAt !== state.lastUpdatedAt) {
          state.lastUpdatedAt = updatedAt;
          state.stableSince = timestamp;
          return false;
        }

        // Application hasn't been updated - check if stable long enough
        const stableDuration = timestamp - (state.stableSince || timestamp);
        return stableDuration >= stabilityMillis;
      },
      {
        timeoutMillis: timeoutMillis * 0.3, // Use remaining 30% of timeout for application sync
        intervalMillis,
        onDone: () => {
          const duration = state.stableSince ? Date.now() - (state.stableSince || 0) : 0;
          console.debug(`Application ${applicationId} sync complete (stable for ${duration}ms)`);
        },
        onRetry: () => {
          // Silent retry - avoid log spam
        },
      }
    );

    // Additional delay to allow ApplicationEvent to be processed by ClientManagerImpl
    // This is necessary because domain sync completion doesn't guarantee that
    // the ApplicationEvent has been processed yet
    await waitFor(eventProcessingDelay);
  } catch (error: any) {
    // Timeout or error - log warning but don't throw (backward compatibility)
    console.warn(`Application ${applicationId} sync timeout after ${timeoutMillis}ms: ${error.message}`);
  }
};

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
