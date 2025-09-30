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
import { createDomain, deleteDomain, startDomain} from '@management-commands/domain-management-commands';
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
  RateLimitConfig
} from './fixtures/rate-limit-fixture';

global.fetch = fetch;

let accessToken;
let domain;
let application;
let openIdConfiguration;

jest.setTimeout(200000);

beforeAll(async () => {
  // Setup: Create domain, application, and get OpenID configuration
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, 'rate-limit-test-domain', 'Rate limiting test domain');
  const domainStarted = await startDomain(createdDomain.id, accessToken);
  domain = domainStarted;

  // Wait for domain to be ready
  await new Promise((r) => setTimeout(r, 10000));

  // Create backend-to-backend application using fixture
  application = await createServiceApplication(
    domain.id, 
    accessToken, 
    'rate-limit-b2b-app', 
    'rate-limit-app', 
    'rate-limit-app'
  );

  // Get OpenID configuration
  const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
  openIdConfiguration = result.body;
});

afterAll(async () => {
    if (domain && domain.id) {
      await deleteDomain(domain.id, accessToken);
    }
});

/**
 * Default rate limit configuration for tests
 */
const DEFAULT_RATE_LIMIT_CONFIG: RateLimitConfig = {
    keyExpression: "{#request.headers['test-rate-limit-key']}",
    limit: 2,
    periodSeconds: 3
  };

describe('Rate Limiting Policy Tests', () => {

  it('Should enforce rate limiting when no rate limit key header is set', async () => {
    // Configure rate limiting policy
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    // Make requests WITHOUT the rate limit key header
    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4);

    // All requests should be treated as the same key (undefined/null)
    const results = analyzeRateLimitResults(requests);
    expect(results.successCount).toEqual(2);
    expect(results.rateLimitedCount).toEqual(2);
    
    // Verify rate limit headers are present
    verifyRateLimitHeaders(requests, 2);
  });

  it('Should enforce rate limiting when rate limit key is set to key1', async () => {
    // Configure rate limiting policy
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    // Make requests WITH the same rate limit key header
    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4, {
      headers: { 'test-rate-limit-key': 'key1' }
    });

    // Should enforce rate limiting for this specific key
    const results = analyzeRateLimitResults(requests);
    expect(results.successCount).toEqual(2);
    expect(results.rateLimitedCount).toEqual(2);
    
    // Verify rate limit headers are present
    verifyRateLimitHeaders(requests, 2);
  });

  it('Should enforce separate rate limits for different keys (key1 vs key2)', async () => {
    // Configure rate limiting policy
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    // Test key1: Make requests with 'key1' header
    const key1Requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'key1' }
    });

    // Test key2: Make requests with 'key2' header  
    const key2Requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'key2' }
    });

    // Both keys should have their own independent rate limits
    const key1Results = analyzeRateLimitResults(key1Requests);
    const key2Results = analyzeRateLimitResults(key2Requests);
    
    expect(key1Results.successCount).toEqual(2);
    expect(key1Results.rateLimitedCount).toEqual(1);
    
    expect(key2Results.successCount).toEqual(2);
    expect(key2Results.rateLimitedCount).toEqual(1);
    
    // Verify that each key has independent rate limiting with proper headers
    verifyRateLimitHeaders(key1Requests, 2);
    verifyRateLimitHeaders(key2Requests, 2);
  });

  it('Should reset rate limit after time window expires', async () => {
    // Configure rate limiting policy
    await configureRateLimitPolicy(domain.id, accessToken, DEFAULT_RATE_LIMIT_CONFIG);

    // Phase 1: Exhaust the rate limit
    console.log('🔍 Phase 1: Exhausting rate limit...');
    const initialRequests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'recovery-test-key' }
    });

    const initialResults = analyzeRateLimitResults(initialRequests);
    expect(initialResults.successCount).toEqual(2);
    expect(initialResults.rateLimitedCount).toEqual(1);
    console.log(`✅ Rate limit exhausted: ${initialResults.successCount} success, ${initialResults.rateLimitedCount} rate limited`);

    // Phase 2: Wait for rate limit window to reset
    console.log('⏳ Phase 2: Waiting for rate limit window to reset...');
    await waitForRateLimitReset(4); // Wait 4 seconds (1 second buffer)

    // Phase 3: Make new requests to verify rate limit has reset
    console.log('🔍 Phase 3: Testing rate limit recovery...');
    const recoveryRequests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 3, {
      headers: { 'test-rate-limit-key': 'recovery-test-key' }
    });

    const recoveryResults = analyzeRateLimitResults(recoveryRequests);
    expect(recoveryResults.successCount).toEqual(2);
    expect(recoveryResults.rateLimitedCount).toEqual(1);
    console.log(`✅ Rate limit recovered: ${recoveryResults.successCount} success, ${recoveryResults.rateLimitedCount} rate limited`);

    // Verify that the rate limit headers show fresh counts after reset
    const firstRecoveryRequest = recoveryRequests[0];
    expect(firstRecoveryRequest.headers['x-rate-limit-limit']).toBe('2');
    expect(firstRecoveryRequest.headers['x-rate-limit-remaining']).toBe('1');
    console.log('✅ Rate limit headers show fresh counts after reset');
  });

  it('Should enforce rate limiting without response headers when addHeaders is disabled', async () => {
    // Configure rate limiting policy with headers disabled
    const configWithHeadersDisabled = { ...DEFAULT_RATE_LIMIT_CONFIG, addHeaders: false };
    await configureRateLimitPolicy(domain.id, accessToken, configWithHeadersDisabled);

    // Make requests to test rate limiting behavior
    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4, {
      headers: { 'test-rate-limit-key': 'no-headers-test-key' }
    });

    // Rate limiting should still work (2 success, 2 rate limited)
    const results = analyzeRateLimitResults(requests);
    expect(results.successCount).toEqual(2);
    expect(results.rateLimitedCount).toEqual(2);
    
    // But headers should NOT be present when addHeaders is disabled
    verifyRateLimitHeadersMissing(requests);
    
    // Error response should still be correct even without headers
    const rateLimitedRequests = requests.filter(req => req.status === 429);
    expect(rateLimitedRequests[0].body.error).toBe('invalid_grant');
    expect(rateLimitedRequests[0].body.error_description).toContain('Rate limit exceeded');
    
    console.log('✅ Rate limiting works without headers when addHeaders is disabled');
  });

  it('Should use dynamicLimit when static limit is 0', async () => {
    // Configure rate limiting policy with dynamic limit (limit must be 0)
    const dynamicConfig: RateLimitConfig = {
      keyExpression: "{#request.headers['test-rate-limit-key']}",
      limit: 0, // Must be 0 for dynamicLimit to work
      periodSeconds: 3,
      dynamicLimit: '4'
    };
    
    await configureRateLimitPolicy(domain.id, accessToken, dynamicConfig);

    // Make requests to test dynamic rate limiting
    const requests = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 6, {
      headers: { 
        'test-rate-limit-key': 'dynamic-limit-test'
      }
    });

    const results = analyzeRateLimitResults(requests);
    const firstRequest = requests[0];
    const actualLimit = parseInt(firstRequest.headers['x-rate-limit-limit']);
    
    // Should use dynamicLimit value of 4
    expect(actualLimit).toBe(4);
    expect(results.successCount).toEqual(4);
    expect(results.rateLimitedCount).toEqual(2);
    
    console.log('✅ Dynamic rate limiting works when limit is 0');
  });

  it('Should apply dynamicLimit with EL expression', async () => {
    // Configure rate limiting to read limit from request header
    const headerDynamicConfig: RateLimitConfig = {
      keyExpression: "{#request.headers['test-rate-limit-key']}",
      limit: 0,
      periodSeconds: 3,
      dynamicLimit: "{#request.headers['x-test-ratelimit']}"
    };

    await configureRateLimitPolicy(domain.id, accessToken, headerDynamicConfig);

    // Case 1: Header sets limit to 5
    const requestsLimit5 = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 6, {
      headers: {
        'test-rate-limit-key': 'hdr-limit-5',
        'x-test-ratelimit': '5'
      }
    });

    const resultLimit5 = analyzeRateLimitResults(requestsLimit5);
    const firstReq5 = requestsLimit5[0];
    expect(firstReq5.headers['x-rate-limit-limit']).toBe('5');
    expect(resultLimit5.successCount).toEqual(5);
    expect(resultLimit5.rateLimitedCount).toEqual(1);

    // Case 2: Header sets limit to 2
    const requestsLimit2 = await makeTokenRequests(openIdConfiguration.token_endpoint, application, 4, {
      headers: {
        'test-rate-limit-key': 'hdr-limit-2',
        'x-test-ratelimit': '2'
      }
    });

    const resultLimit2 = analyzeRateLimitResults(requestsLimit2);
    const firstReq2 = requestsLimit2[0];
    expect(firstReq2.headers['x-rate-limit-limit']).toBe('2');
    expect(resultLimit2.successCount).toEqual(2);
    expect(resultLimit2.rateLimitedCount).toEqual(2);
  });

  it('Should enforce rate limiting in async mode with concurrent requests', async () => {
    // Test async (non-strict) mode with more requests to ensure we see blocking
    const asyncConfig: RateLimitConfig = {
      keyExpression: "{#request.headers['test-rate-limit-key']}",
      limit: 2,
      periodSeconds: 3,
      async: true
    };
    await configureRateLimitPolicy(domain.id, accessToken, asyncConfig);

    // Fire 6 concurrent requests to ensure we exceed the limit and see blocking
    const asyncRequests = await makeConcurrentTokenRequests(openIdConfiguration.token_endpoint, application, 6, {
      headers: { 'test-rate-limit-key': 'async-concurrent-test' }
    });
    const asyncResults = analyzeRateLimitResults(asyncRequests);

    // Verify async mode rate limiting behavior:
    // - successCount should be at least the limit (2) - async mode should allow at least the configured limit
    // - rateLimitedCount should be at least 1 (proving rate limiting is working)
    // - Total should equal 6
    expect(asyncResults.successCount).toBeGreaterThanOrEqual(2);
    expect(asyncResults.rateLimitedCount).toBeGreaterThanOrEqual(1);
    expect(asyncResults.successCount + asyncResults.rateLimitedCount).toBe(6);

    // Verify that we have at least one rate limited request (proves rate limiting is working)
    const rateLimitedRequests = asyncResults.rateLimitedRequests;
    expect(rateLimitedRequests.length).toBeGreaterThan(0);
    const firstLimited = rateLimitedRequests[0];
    expect(firstLimited.status).toBe(429);
    expect(firstLimited.body.error).toBe('invalid_grant');
    // Check rate-limited headers: remaining should be 0 when limit is reached
    expect(firstLimited.headers['x-rate-limit-limit']).toBe('2');
    expect(firstLimited.headers['x-rate-limit-remaining']).toBe('0');
    expect(firstLimited.headers['x-rate-limit-reset']).toBeDefined();
  });
});
