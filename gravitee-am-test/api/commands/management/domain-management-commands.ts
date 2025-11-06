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
import { retryUntil, RetryOptions } from '@utils-commands/retry';
import { getWellKnownOpenIdConfiguration, performPost } from '@gateway-commands/oauth-oidc-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { expect } from '@jest/globals';
import faker from 'faker';

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
  // Use configurable timeout for domain startup - increase for parallel test execution
  const timeoutMs = Number.parseInt(process.env.AM_DOMAIN_START_TIMEOUT || '30000', 10);
  return retryUntil(
    () => getWellKnownOpenIdConfiguration(domain.hrid) as Promise<any>,
    (res) => res.status == 200,
    {
      timeoutMillis: timeoutMs,
      onDone: () => console.log(`domain "${domain.hrid}" ready after ${(Date.now() - start) / 1000}s`),
      onRetry: () => console.debug(`domain "${domain.hrid}" not ready yet`),
    },
  ).then((response) => ({ domain, oidcConfig: response.body }));
};

/**
 * Wait for domain sync by polling a verification function
 * Generic helper that polls until a condition is met
 * @param verifyFn - Function that returns a promise with the value to check
 * @param isComplete - Function that returns true when sync is complete
 * @param options - Retry options
 */
export async function waitForDomainSyncWithVerification<T>(
  verifyFn: () => Promise<T>,
  isComplete: (result: T) => boolean,
  options: RetryOptions<T> = {}
): Promise<T> {
  const { timeoutMillis = 30000, intervalMillis = 500 } = options;
  const start = Date.now();

  return retryUntil(
    verifyFn,
    isComplete,
    {
      timeoutMillis,
      intervalMillis,
      onDone: () => console.log(`Domain sync confirmed after ${(Date.now() - start) / 1000}s`),
      onRetry: () => console.debug('Domain sync not complete, waiting...'),
    }
  );
}

/**
 * Wait for domain sync with configurable timeout
 * Uses environment variable AM_DOMAIN_SYNC_TIMEOUT (default 30 seconds)
 * @param timeoutMs - Optional timeout in milliseconds (overrides env var)
 */
export const waitForDomainSync = (timeoutMs?: number) => {
  const timeout = timeoutMs ?? Number.parseInt(process.env.AM_DOMAIN_SYNC_TIMEOUT || '30000', 10);
  return waitFor(timeout);
};
export const waitFor = (duration) => new Promise((r) => setTimeout(r, duration));

/**
 * Wait for token revocation to complete by polling token endpoint
 * Polls until trying to use the refresh token returns 400 with 'invalid_grant'
 * @param tokenEndpoint - Token endpoint URL
 * @param refreshToken - Refresh token to check
 * @param clientAuth - Client authentication header (e.g., 'Basic base64(clientId:clientSecret)')
 * @param options - Retry options
 */
export async function waitForTokenRevocation(
  tokenEndpoint: string,
  refreshToken: string,
  clientAuth: string,
  options: RetryOptions<any> = {}
): Promise<void> {
  const { timeoutMillis = 30000, intervalMillis = 500 } = options;
  const start = Date.now();

  return retryUntil(
    async () => {
      try {
        // Try to use the refresh token - if it's revoked, we'll get 400 with 'invalid_grant'
        await performPost(
          tokenEndpoint,
          '',
          `grant_type=refresh_token&refresh_token=${refreshToken}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: clientAuth,
          }
        ).expect(200);
        // If we get a 200, token is still valid (not revoked yet)
        return { status: 200, revoked: false, error: null };
      } catch (err: any) {
        // If we get 400 with 'invalid_grant', token is revoked (success!)
        if (err.response?.status === 400 && err.response?.body?.error === 'invalid_grant') {
          return { status: 400, revoked: true, error: 'invalid_grant' };
        }
        // Other errors should be re-thrown
        throw err;
      }
    },
    (result) => result.revoked === true, // Success when token is revoked
    {
      timeoutMillis,
      intervalMillis,
      onDone: () => console.log(`Token revocation confirmed after ${(Date.now() - start) / 1000}s`),
      onRetry: () => console.debug('Token still valid, waiting for revocation...'),
    }
  ).then(() => {}); // Return void
}

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
