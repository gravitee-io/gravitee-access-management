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
import { getDomainFlows, updateDomainFlows, startDomain } from '@management-commands/domain-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { FlowEntityTypeEnum } from '../../../api/management/models';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

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
  clientSecret: string
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

  // Verify application was created successfully
  expect(application).toBeDefined();
  expect(application.id).toBeDefined();
  expect(application.settings.oauth.clientId).toBe(clientId);
  expect(application.settings.oauth.clientSecret).toBe(clientSecret);
  expect(application.settings.oauth.grantTypes).toEqual(['client_credentials']);

  return application;
};

/**
 * Configure rate limiting policy on a domain
 */
export const configureRateLimitPolicy = async (
  domainId: string,
  accessToken: string,
  config: RateLimitConfig
) => {
  const flows = await getDomainFlows(domainId, accessToken);
  const rateLimitConfig = {
    async: config.async ?? false,
    addHeaders: config.addHeaders ?? true,
    rate: {
      useKeyOnly: true,
      periodTime: config.periodSeconds ?? 3,
      periodTimeUnit: "SECONDS",
      key: config.keyExpression,
      limit: config.limit ?? 2,
      dynamicLimit: config.dynamicLimit ?? 2
    }
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

  // Restart domain to apply policy
  const restartedDomain = await startDomain(domainId, accessToken);
  await new Promise((r) => setTimeout(r, 10000));
  
  return restartedDomain;
};

/**
 * Make multiple token requests with specified options
 */
export const makeTokenRequests = async (
  tokenEndpoint: string,
  application: any,
  count: number,
  options: TokenRequestOptions = {}
) => {
  const requests = [];
  const {
    grantType = 'client_credentials',
    headers = {},
    body = `grant_type=${grantType}`
  } = options;

  for (let i = 0; i < count; i++) {
    const response = await performPost(
      tokenEndpoint,
      '',
      body,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(application),
        ...headers,
      }
    );
    requests.push(response);
  }
  return requests;
};

/**
 * Analyze rate limiting results from requests
 */
export const analyzeRateLimitResults = (requests: any[]) => {
  const successCount = requests.filter(req => req.status === 200).length;
  const rateLimitedCount = requests.filter(req => req.status === 429).length;
  const successfulRequests = requests.filter(req => req.status === 200);
  const rateLimitedRequests = requests.filter(req => req.status === 429);

  return {
    successCount,
    rateLimitedCount,
    successfulRequests,
    rateLimitedRequests,
    totalRequests: requests.length
  };
};

/**
 * Verify rate limiting headers on successful requests
 */
export const verifyRateLimitHeaders = (requests: any[], expectedLimit: number) => {
  const successfulRequests = requests.filter(req => req.status === 200);
  
  if (successfulRequests.length > 0) {
    const firstRequest = successfulRequests[0];
    expect(firstRequest.headers['x-rate-limit-limit']).toBe(expectedLimit.toString());
    expect(firstRequest.headers['x-rate-limit-remaining']).toBeDefined();
    expect(parseInt(firstRequest.headers['x-rate-limit-remaining'])).toBeLessThanOrEqual(expectedLimit);
  }
};

/**
 * Verify that rate limit headers are missing from requests
 */
export const verifyRateLimitHeadersMissing = (requests: any[]) => {
  const successfulRequests = requests.filter(req => req.status === 200);
  const rateLimitedRequests = requests.filter(req => req.status === 429);
  
  // Check that rate limit headers are missing from successful requests
  if (successfulRequests.length > 0) {
    expect(successfulRequests[0].headers['x-rate-limit-limit']).toBeUndefined();
    expect(successfulRequests[0].headers['x-rate-limit-remaining']).toBeUndefined();
    expect(successfulRequests[0].headers['x-rate-limit-reset']).toBeUndefined();
  }
  
  // Check that rate limit headers are missing from rate limited requests
  if (rateLimitedRequests.length > 0) {
    expect(rateLimitedRequests[0].headers['x-rate-limit-limit']).toBeUndefined();
    expect(rateLimitedRequests[0].headers['x-rate-limit-remaining']).toBeUndefined();
    expect(rateLimitedRequests[0].headers['x-rate-limit-reset']).toBeUndefined();
  }
};

/**
 * Wait for rate limit window to reset
 */
export const waitForRateLimitReset = async (seconds: number) => {
  console.log(`⏳ Waiting ${seconds} seconds for rate limit window to reset...`);
  await new Promise((r) => setTimeout(r, seconds * 1000));
  console.log('✅ Rate limit window reset');
};
