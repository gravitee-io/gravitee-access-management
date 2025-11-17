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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { CorsFixture, setupCorsFixture } from './fixtures/cors-fixture';
import { performOptions, performGet } from '@gateway-commands/oauth-oidc-commands';

global.fetch = fetch;

let fixture: CorsFixture;

jest.setTimeout(200000);

beforeAll(async () => {
  fixture = await setupCorsFixture();
});

describe('CORS - Default configuration', () => {
  it('should return default CORS headers on OPTIONS request for newly created domain', async () => {
    const response = await performOptions(process.env.AM_GATEWAY_URL, fixture.userinfoPath, {
      Origin: 'https://any-origin.com',
      'Access-Control-Request-Method': 'GET',
      'Access-Control-Request-Headers': 'Authorization',
    });

    expect(response.status).toBe(204);
    expect(response.headers['access-control-allow-origin']).toBe('*');

    // Check default headers
    const allowHeaders = response.headers['access-control-allow-headers'];
    expect(allowHeaders).toBeDefined();
    expect(allowHeaders.toLowerCase().split(',').sort()).toEqual([
        'authorization',
        'cache-control',
        'content-type',
        'if-match',
        'origin',
        'pragma',
        'x-requested-with',
        'x-xsrf-token',
      ]);

    // Check default methods
    const allowMethods = response.headers['access-control-allow-methods'];
    expect(allowMethods).toBeDefined();
    expect(allowMethods.toUpperCase().split(',').sort()).toEqual(['DELETE', 'GET', 'PATCH', 'POST', 'PUT']);

    // Check default allow credentials
    expect(response.headers['access-control-allow-credentials']).not.toBe('true');

    // Check default max age
    expect(response.headers['access-control-max-age']).toBe('86400');
  });
});

describe('CORS - Successful requests', () => {
  it('should allow requests when CORS is disabled', async () => {
    await fixture.updateCorsSettings({
      enabled: false,
    });

    const optionsResponse = await performOptions(process.env.AM_GATEWAY_URL, fixture.userinfoPath, {
      Origin: 'https://any-origin.com',
      'Access-Control-Request-Method': 'GET',
    });

    expect(optionsResponse.status).toBe(204);

    const accessToken = await fixture.getAccessToken();
    const getResponse = await performGet(
      process.env.AM_GATEWAY_URL,
      fixture.userinfoPath,
      {
        Authorization: `Bearer ${accessToken}`,
        Origin: 'https://any-origin.com',
      },
    );

    expect(getResponse.status).toBe(200);
  });

  it('should allow requests with wildcard origin', async () => {
    await fixture.updateCorsSettings({
      enabled: true,
      allowedOrigins: new Set(['*']),
    });
    const optionsResponse = await performOptions(process.env.AM_GATEWAY_URL, fixture.userinfoPath, {
      Origin: 'https://any-origin.com',
      'Access-Control-Request-Method': 'GET',
      'Access-Control-Request-Headers': 'Authorization',
    });

    expect(optionsResponse.status).toBe(204);
    expect(optionsResponse.headers['access-control-allow-origin']).toBe('*');
    expect(optionsResponse.headers['access-control-allow-methods']).toBeDefined();
    expect(optionsResponse.headers['access-control-allow-headers']).toBeDefined();

    const accessToken = await fixture.getAccessToken();
    const getResponse = await performGet(
      process.env.AM_GATEWAY_URL,
      fixture.userinfoPath,
      {
        Authorization: `Bearer ${accessToken}`,
        Origin: 'https://any-origin.com',
      },
    );

    expect(getResponse.status).toBe(200);
    expect(getResponse.headers['access-control-allow-origin']).toBe('*');
  });

  it('should allow requests with matching origin', async () => {
    await fixture.updateCorsSettings({
      enabled: true,
      allowedOrigins: new Set(['https://example.com']),
      allowedMethods: new Set(['GET', 'OPTIONS']),
      allowedHeaders: new Set(['Authorization', 'Content-Type']),
      allowCredentials: true,
      maxAge: 3600,
    });
    const optionsResponse = await performOptions(process.env.AM_GATEWAY_URL, fixture.userinfoPath, {
      Origin: 'https://example.com',
      'Access-Control-Request-Method': 'GET',
      'Access-Control-Request-Headers': 'Authorization',
    });

    expect(optionsResponse.status).toBe(204);
    expect(optionsResponse.headers['access-control-allow-origin']).toBe('https://example.com');
    const allowMethods = optionsResponse.headers['access-control-allow-methods'];
    expect(allowMethods).toBeDefined();
    expect(allowMethods.toUpperCase()).toContain('GET');
    expect(allowMethods.toUpperCase()).toContain('OPTIONS');
    const allowHeaders = optionsResponse.headers['access-control-allow-headers'];
    expect(allowHeaders).toBeDefined();
    expect(allowHeaders.toLowerCase()).toContain('authorization');
    expect(allowHeaders.toLowerCase()).toContain('content-type');
    expect(optionsResponse.headers['access-control-allow-credentials']).toBe('true');
    expect(optionsResponse.headers['access-control-max-age']).toBe('3600');

    const accessToken = await fixture.getAccessToken();
    const getResponse = await performGet(
      process.env.AM_GATEWAY_URL,
      fixture.userinfoPath,
      {
        Authorization: `Bearer ${accessToken}`,
        Origin: 'https://example.com',
      },
    );

    expect(getResponse.status).toBe(200);
    expect(getResponse.headers['access-control-allow-origin']).toBe('https://example.com');
  });
});

describe('CORS - Rejected requests (403)', () => {
  it('should reject requests with invalid origin', async () => {
    await fixture.updateCorsSettings({
      enabled: true,
      allowedOrigins: new Set(['https://example.com']),
      allowedMethods: new Set(['GET', 'OPTIONS']),
      allowedHeaders: new Set(['Authorization', 'Content-Type']),
    });
    const optionsResponse = await performOptions(process.env.AM_GATEWAY_URL, fixture.userinfoPath, {
      Origin: 'https://malicious.com',
      'Access-Control-Request-Method': 'GET',
      'Access-Control-Request-Headers': 'Authorization',
    });

    expect(optionsResponse.status).toBe(403);
    expect(optionsResponse.res.statusMessage).toBe('CORS Rejected - Invalid origin');

    const accessToken = await fixture.getAccessToken();
    await performGet(
      process.env.AM_GATEWAY_URL,
      fixture.userinfoPath,
      {
        Authorization: `Bearer ${accessToken}`,
        Origin: 'https://malicious.com',
      },
    ).expect(403);
  });
});

afterAll(async () => {
  await fixture.cleanup();
});
