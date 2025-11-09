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
import { expect } from '@jest/globals';
import { getDomainFlows, updateDomainFlows, startDomain, waitForDomainStart, waitForDomainSync, waitForApplicationSync } from '@management-commands/domain-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { FlowEntityTypeEnum } from '../../../api/management/models';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { retryUntil } from '@utils-commands/retry';

export interface RateLimitConfig {
  keyExpression: string;
  limit?: number;
  periodSeconds?: number;
  async?: boolean;
  addHeaders?: boolean;
  dynamicLimit?: string;
}

export interface TokenRequestOptions {
  grantType?: string;
  headers?: Record<string, string>;
  body?: string;
}

/**
 * Create a SERVICE application for client_credentials grant type
 */
export const createServiceApplication = async (
  domainId: string,
  accessToken: string,
  name: string,
  clientId: string,
  clientSecret: string,
) => {
  const application = await createApplication(domainId, accessToken, {
    name,
    type: 'SERVICE',
    clientId,
    clientSecret,
  }).then((app) =>
    updateApplication(
      domainId,
      accessToken,
      {
        settings: {
          oauth: {
            grantTypes: ['client_credentials'],
          },
        },
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  expect(application).toBeDefined();
  expect(application.id).toBeDefined();
  expect(application.settings.oauth.clientId).toBe(clientId);
  expect(application.settings.oauth.clientSecret).toBe(clientSecret);
  expect(application.settings.oauth.grantTypes).toEqual(['client_credentials']);

  return application;
};

/**
 * Configure rate limiting policy on a domain
 * @param domainId - Domain ID
 * @param accessToken - Access token
 * @param config - Rate limit configuration
 * @param applicationId - Optional application ID to wait for sync after restart
 */
export const configureRateLimitPolicy = async (domainId: string, accessToken: string, config: RateLimitConfig, applicationId?: string) => {
  const flows = await getDomainFlows(domainId, accessToken);
  const rateLimitConfig = {
    async: config.async ?? false,
    addHeaders: config.addHeaders ?? true,
    rate: {
      useKeyOnly: true,
      periodTime: config.periodSeconds ?? 2,
      periodTimeUnit: 'SECONDS',
      key: config.keyExpression,
      limit: config.limit ?? 2,
      dynamicLimit: config.dynamicLimit ?? 2,
    },
  };

  lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Token, 'pre', [
    {
      name: 'Rate Limit Policy',
      policy: 'rate-limit',
      description: 'Rate limit token requests',
      condition: '',
      enabled: true,
      configuration: JSON.stringify(rateLimitConfig),
    },
  ]);

  await updateDomainFlows(domainId, accessToken, flows);
  await waitForDomainSync(domainId, accessToken);

  // Restart domain to apply policy and wait for it to be ready
  const restartedDomain = await startDomain(domainId, accessToken);
  const domainReady = await waitForDomainStart(restartedDomain);
  
  // Wait for domain sync after restart to ensure flows are synced
  await waitForDomainSync(domainId, accessToken);
  
  // If application ID is provided, wait for application sync after restart
  // This ensures the application is available in the gateway after domain restart
  if (applicationId) {
    await waitForApplicationSync(domainId, accessToken, applicationId);
  }
  
  // Additional delay to ensure rate limit policy is fully applied
  // Domain sync doesn't guarantee policy application is complete
  // Flows need time to be loaded after domain restart
  // This is especially important for async mode
  await new Promise((resolve) => setTimeout(resolve, 2000));

  // Verify the token endpoint is accessible before returning
  // This ensures the domain is fully ready to serve requests
  await verifyTokenEndpointReady(domainReady.oidcConfig.token_endpoint);

  return domainReady.domain;
};

/**
 * Make multiple token requests with specified options
 */
export const makeTokenRequests = async (tokenEndpoint: string, application: any, count: number, options: TokenRequestOptions = {}) => {
  const requests = [];
  const { grantType = 'client_credentials', headers = {}, body = `grant_type=${grantType}` } = options;

  for (let i = 0; i < count; i++) {
    const response = await performPost(tokenEndpoint, '', body, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(application),
      ...headers,
    });
    requests.push(response);
  }
  return requests;
};

/**
 * Make multiple token requests concurrently
 */
export const makeConcurrentTokenRequests = async (
  tokenEndpoint: string,
  application: any,
  count: number,
  options: TokenRequestOptions = {},
) => {
  const { grantType = 'client_credentials', headers = {}, body = `grant_type=${grantType}` } = options;

  const promises = Array.from({ length: count }).map(() =>
    performPost(tokenEndpoint, '', body, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(application),
      ...headers,
    }),
  );
  return await Promise.all(promises);
};

/**
 * Analyze rate limiting results from requests
 */
export const analyzeRateLimitResults = (requests: any[]) => {
  const successCount = requests.filter((req) => req.status === 200).length;
  const rateLimitedCount = requests.filter((req) => req.status === 429).length;
  const successfulRequests = requests.filter((req) => req.status === 200);
  const rateLimitedRequests = requests.filter((req) => req.status === 429);

  return {
    successCount,
    rateLimitedCount,
    successfulRequests,
    rateLimitedRequests,
    totalRequests: requests.length,
  };
};

/**
 * Verify rate limiting headers on successful requests
 */
export const verifyRateLimitHeaders = (requests: any[], expectedLimit: number) => {
  const successfulRequests = requests.filter((req) => req.status === 200);

  if (successfulRequests.length > 0) {
    const firstRequest = successfulRequests[0];
    expect(firstRequest.headers['x-rate-limit-limit']).toBe(expectedLimit.toString());
    expect(firstRequest.headers['x-rate-limit-remaining']).toBeDefined();
    expect(Number.parseInt(firstRequest.headers['x-rate-limit-remaining'], 10)).toBeLessThanOrEqual(expectedLimit);
  }
};

/**
 * Verify that rate limit headers are missing from requests
 */
export const verifyRateLimitHeadersMissing = (requests: any[]) => {
  const successfulRequests = requests.filter((req) => req.status === 200);
  const rateLimitedRequests = requests.filter((req) => req.status === 429);

  if (successfulRequests.length > 0) {
    expect(successfulRequests[0].headers['x-rate-limit-limit']).toBeUndefined();
    expect(successfulRequests[0].headers['x-rate-limit-remaining']).toBeUndefined();
    expect(successfulRequests[0].headers['x-rate-limit-reset']).toBeUndefined();
  }

  if (rateLimitedRequests.length > 0) {
    expect(rateLimitedRequests[0].headers['x-rate-limit-limit']).toBeUndefined();
    expect(rateLimitedRequests[0].headers['x-rate-limit-remaining']).toBeUndefined();
    expect(rateLimitedRequests[0].headers['x-rate-limit-reset']).toBeUndefined();
  }
};

/**
 * Verify token endpoint is accessible and ready to serve requests
 * @param tokenEndpoint - Token endpoint URL
 */
const verifyTokenEndpointReady = async (tokenEndpoint: string | undefined): Promise<void> => {
  if (!tokenEndpoint) {
    return;
  }

  try {
    await retryUntil(
      async () => {
        // Try a simple request to verify the endpoint is accessible
        // We expect it to fail with 401 (unauthorized) rather than 404 (not found)
        // This confirms the endpoint exists and is ready
        const testRequest = await performPost(tokenEndpoint, '', 'grant_type=client_credentials', {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic invalid',
        });
        // If we get 401, the endpoint is ready (it's rejecting due to invalid auth, not because it doesn't exist)
        // If we get 404, the endpoint isn't ready yet
        return testRequest.status === 401 || testRequest.status === 400;
      },
      (isReady) => isReady === true,
      {
        timeoutMillis: 10000,
        intervalMillis: 500,
        onRetry: () => console.debug('Token endpoint not ready yet, retrying...'),
      },
    );
  } catch (error: any) {
    // If verification fails, log a warning but don't throw
    // The test will fail if the endpoint isn't actually ready
    console.warn(`Token endpoint verification failed: ${error.message}`);
  }
};

/**
 * Wait for rate limit window to reset
 * Uses time-based wait with a buffer to avoid consuming rate limit quota
 * @param periodSeconds - Rate limit period in seconds (default: 2)
 * @param bufferSeconds - Additional buffer time in seconds (default: 1)
 */
export const waitForRateLimitReset = async (
  periodSeconds: number = 2,
  bufferSeconds: number = 1
): Promise<void> => {
  const waitTime = (periodSeconds + bufferSeconds) * 1000;
  await new Promise((resolve) => setTimeout(resolve, waitTime));
};
