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
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import {
  configureRateLimitPolicy,
  makeTokenRequests,
  makeConcurrentTokenRequests,
  analyzeRateLimitResults,
  verifyRateLimitHeaders,
  verifyRateLimitHeadersMissing,
  createServiceApplication,
  waitForRateLimitReset,
  RateLimitConfig,
} from './fixtures/rate-limit-fixture';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

let accessToken;
let domain;
let application;
let openIdConfiguration;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('rate-limit', true), 'Rate limiting test domain');
  const domainStarted = await startDomain(createdDomain.id, accessToken);
  domain = domainStarted;

  await waitForDomainSync(domain.id, accessToken);

  application = await createServiceApplication(domain.id, accessToken, 'rate-limit-b2b-app', 'rate-limit-app', 'rate-limit-app');
  await waitForDomainSync(domain.id, accessToken);

  const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
  openIdConfiguration = result.body;
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

/**
 * Default rate limit configuration for tests
 */
const DEFAULT_RATE_LIMIT_CONFIG: RateLimitConfig = {
  keyExpression: "{#request.headers['test-rate-limit-key']}",
  limit: 2,
  periodSeconds: 2,
};

describe('Rate Limiting Policy Tests', () => {
  it('Should enforce rate limiting when no rate limit key header is set', async () => {
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4);
    const results = analyzeRateLimitResults(requests);
    expect(results.successCount).toEqual(2);
    expect(results.rateLimitedCount).toEqual(2);

    verifyRateLimitHeaders(requests, 2);
  });

  it('Should enforce rate limiting when rate limit key is set to key1', async () => {
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4, {
      headers: { 'test-rate-limit-key': 'key1' },
    });

    const results = analyzeRateLimitResults(requests);
    expect(results.successCount).toEqual(2);
    expect(results.rateLimitedCount).toEqual(2);

    verifyRateLimitHeaders(requests, 2);
  });

  it('Should enforce separate rate limits for different keys (key1 vs key2)', async () => {
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    const key1Requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'key1' },
    });

    const key2Requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'key2' },
    });

    const key1Results = analyzeRateLimitResults(key1Requests);
    const key2Results = analyzeRateLimitResults(key2Requests);

    expect(key1Results.successCount).toEqual(2);
    expect(key1Results.rateLimitedCount).toEqual(1);

    expect(key2Results.successCount).toEqual(2);
    expect(key2Results.rateLimitedCount).toEqual(1);

    verifyRateLimitHeaders(key1Requests, 2);
    verifyRateLimitHeaders(key2Requests, 2);
  });

  it('Should reset rate limit after time window expires', async () => {
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    console.log('🔍 Phase 1: Exhausting rate limit...');
    const initialRequests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'recovery-test-key' },
    });

    const initialResults = analyzeRateLimitResults(initialRequests);
    expect(initialResults.successCount).toEqual(2);
    expect(initialResults.rateLimitedCount).toEqual(1);
    console.log(`✅ Rate limit exhausted: ${initialResults.successCount} success, ${initialResults.rateLimitedCount} rate limited`);

    console.log('⏳ Phase 2: Waiting for rate limit window to reset...');
    await waitForRateLimitReset(DEFAULT_RATE_LIMIT_CONFIG.periodSeconds || 2);

    console.log('🔍 Phase 3: Testing rate limit recovery...');
    const recoveryRequests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'recovery-test-key' },
    });

    const recoveryResults = analyzeRateLimitResults(recoveryRequests);
    expect(recoveryResults.successCount).toEqual(2);
    expect(recoveryResults.rateLimitedCount).toEqual(1);
    console.log(`✅ Rate limit recovered: ${recoveryResults.successCount} success, ${recoveryResults.rateLimitedCount} rate limited`);

    const firstRecoveryRequest = recoveryRequests[0];
    expect(firstRecoveryRequest.headers['x-rate-limit-limit']).toBe('2');
    expect(firstRecoveryRequest.headers['x-rate-limit-remaining']).toBe('1');
    console.log('✅ Rate limit headers show fresh counts after reset');
  });

  it('Should enforce rate limiting without response headers when addHeaders is disabled', async () => {
    const configWithHeadersDisabled = { ...DEFAULT_RATE_LIMIT_CONFIG, addHeaders: false };
    await configureRateLimitPolicy(domain.id, accessToken, configWithHeadersDisabled);

    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4, {
      headers: { 'test-rate-limit-key': 'no-headers-test-key' },
    });

    const results = analyzeRateLimitResults(requests);
    expect(results.successCount).toEqual(2);
    expect(results.rateLimitedCount).toEqual(2);

    verifyRateLimitHeadersMissing(requests);

    const rateLimitedRequest = requests.find((req) => req.status === 429);
    expect(rateLimitedRequest?.body.error).toBe('invalid_grant');
    expect(rateLimitedRequest?.body.error_description).toContain('Rate limit exceeded');

    console.log('✅ Rate limiting works without headers when addHeaders is disabled');
  });

  it('Should use dynamicLimit when static limit is 0', async () => {
    const dynamicConfig: RateLimitConfig = {
      keyExpression: "{#request.headers['test-rate-limit-key']}",
      limit: 0,
      periodSeconds: 2,
      dynamicLimit: '4',
    };

    await configureRateLimitPolicy(domain.id, accessToken, dynamicConfig);

    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 6, {
      headers: {
        'test-rate-limit-key': 'dynamic-limit-test',
      },
    });

    const results = analyzeRateLimitResults(requests);
    const firstRequest = requests[0];
    const actualLimit = Number.parseInt(firstRequest.headers['x-rate-limit-limit'], 10);

    expect(actualLimit).toBe(4);
    expect(results.successCount).toEqual(4);
    expect(results.rateLimitedCount).toEqual(2);

    console.log('✅ Dynamic rate limiting works when limit is 0');
  });

  it('Should apply dynamicLimit with EL expression', async () => {
    const headerDynamicConfig: RateLimitConfig = {
      keyExpression: "{#request.headers['test-rate-limit-key']}",
      limit: 0,
      periodSeconds: 2,
      dynamicLimit: "{#request.headers['x-test-ratelimit']}",
    };

    await configureRateLimitPolicy(domain.id, accessToken, headerDynamicConfig);

    const requestsLimit5 = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 6, {
      headers: {
        'test-rate-limit-key': 'hdr-limit-5',
        'x-test-ratelimit': '5',
      },
    });

    const resultLimit5 = analyzeRateLimitResults(requestsLimit5);
    const firstReq5 = requestsLimit5[0];
    expect(firstReq5.headers['x-rate-limit-limit']).toBe('5');
    expect(resultLimit5.successCount).toEqual(5);
    expect(resultLimit5.rateLimitedCount).toEqual(1);

    const requestsLimit2 = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4, {
      headers: {
        'test-rate-limit-key': 'hdr-limit-2',
        'x-test-ratelimit': '2',
      },
    });

    const resultLimit2 = analyzeRateLimitResults(requestsLimit2);
    const firstReq2 = requestsLimit2[0];
    expect(firstReq2.headers['x-rate-limit-limit']).toBe('2');
    expect(resultLimit2.successCount).toEqual(2);
    expect(resultLimit2.rateLimitedCount).toEqual(2);
  });

  it('Should enforce rate limiting in async mode with concurrent requests', async () => {
    const asyncConfig: RateLimitConfig = {
      keyExpression: "{#request.headers['test-rate-limit-key']}",
      limit: 2,
      periodSeconds: 2,
      async: true,
    };
    await configureRateLimitPolicy(domain.id, accessToken, asyncConfig);

    const asyncRequests = await makeConcurrentTokenRequests(openIdConfiguration.token_endpoint, application, 6, {
      headers: { 'test-rate-limit-key': 'async-concurrent-test' },
    });
    const asyncResults = analyzeRateLimitResults(asyncRequests);

    expect(asyncResults.successCount).toBeGreaterThanOrEqual(2);
    expect(asyncResults.rateLimitedCount).toBeGreaterThanOrEqual(1);
    expect(asyncResults.successCount + asyncResults.rateLimitedCount).toBe(6);

    const rateLimitedRequests = asyncResults.rateLimitedRequests;
    expect(rateLimitedRequests.length).toBeGreaterThan(0);
    const firstLimited = rateLimitedRequests[0];
    expect(firstLimited.status).toBe(429);
    expect(firstLimited.body.error).toBe('invalid_grant');
    expect(firstLimited.headers['x-rate-limit-limit']).toBe('2');
    expect(firstLimited.headers['x-rate-limit-remaining']).toBe('0');
    expect(firstLimited.headers['x-rate-limit-reset']).toBeDefined();
  });
});
