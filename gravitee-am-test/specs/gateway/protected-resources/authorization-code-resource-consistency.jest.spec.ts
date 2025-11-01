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
import { decodeJwt } from '@utils-commands/jwt';
import { validateAudienceClaim } from './fixtures/test-utils';
import { setupProtectedResourcesFixture, ProtectedResourcesFixture } from './fixtures/protected-resources-fixture';

// RFC 8707 Authorization Code Flow: resource consistency between authorization and token requests

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: ProtectedResourcesFixture;

beforeAll(async () => {
  fixture = await setupProtectedResourcesFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Authorization Code Flow - Resource Parameter Consistency (RFC 8707)', () => {
  it('should complete authorization flow with resources and get authorization code', async () => {
    // This test verifies that we can complete the authorization flow with resources
    // and get an authorization code back - this is the critical first step

    const authResources = ['https://api.example.com/photos', 'https://api.example.com/albums'];

    // Step 1: Complete authorization flow with resources
    const authCode = await fixture.completeAuthorizationFlow(authResources);

    // Verify we got an authorization code
    expect(authCode).toBeDefined();
    expect(authCode.length).toBeGreaterThan(0);

    // Step 2: Exchange authorization code for token (without resource parameters)
    // This should use the original authorization resources
    const tokenResponse = await fixture.exchangeAuthCodeForTokenWithoutResources(authCode).expect(200);

    // Verify tokens were issued
    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.refresh_token).toBeDefined();

    // Verify access token aud claim contains original resources
    validateAudienceClaim(tokenResponse.body.access_token, authResources);

    // Verify refresh token orig_resources claim contains original resources
    const refreshTokenDecoded = decodeJwt(tokenResponse.body.refresh_token);
    expect(refreshTokenDecoded.orig_resources).toBeDefined();
    expect(Array.isArray(refreshTokenDecoded.orig_resources)).toBe(true);
    authResources.forEach((resource) => {
      expect(refreshTokenDecoded.orig_resources).toContain(resource);
    });
  });
  it('should allow subset of resources in token request', async () => {
    // Step 1: Authorization request with multiple resources
    const authResources = ['https://api.example.com/photos', 'https://api.example.com/albums'];
    const authCode = await fixture.completeAuthorizationFlow(authResources);

    // Step 2: Token request with subset of resources
    const tokenResources = ['https://api.example.com/photos']; // Subset of authResources
    const tokenResponse = await fixture.exchangeAuthCodeForToken(authCode, tokenResources).expect(200);

    // Verify tokens were issued
    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.refresh_token).toBeDefined();

    // Verify access token aud claim contains subset resources from token request
    const accessTokenDecoded = decodeJwt(tokenResponse.body.access_token);
    const audArray: string[] = Array.isArray(accessTokenDecoded.aud) ? accessTokenDecoded.aud : [accessTokenDecoded.aud];
    tokenResources.forEach((r) => expect(audArray).toContain(r));
    // Should NOT contain resources not in token request
    expect(audArray).not.toContain('https://api.example.com/albums');

    // Verify refresh token orig_resources claim contains original authorization resources
    const refreshTokenDecoded = decodeJwt(tokenResponse.body.refresh_token);
    expect(refreshTokenDecoded.orig_resources).toBeDefined();
    expect(Array.isArray(refreshTokenDecoded.orig_resources)).toBe(true);
    authResources.forEach((resource) => {
      expect(refreshTokenDecoded.orig_resources).toContain(resource);
    });
  });

  it('should deduplicate duplicate resources in token request', async () => {
    const authResources = ['https://api.example.com/photos', 'https://api.example.com/albums'];
    const authCode = await fixture.completeAuthorizationFlow(authResources);

    const dupResources = ['https://api.example.com/photos', 'https://api.example.com/photos'];
    const tokenResponse = await fixture.exchangeAuthCodeForToken(authCode, dupResources).expect(200);
    expect(tokenResponse.body.access_token).toBeDefined();
    const decoded = decodeJwt(tokenResponse.body.access_token);
    const audArray: string[] = Array.isArray(decoded.aud) ? decoded.aud : [decoded.aud];
    expect(audArray).toContain('https://api.example.com/photos');
    // ensure only one entry
    expect(audArray.filter((v) => v === 'https://api.example.com/photos').length).toBe(1);
  });

  it('should reject resources not in original authorization', async () => {
    // Step 1: Authorization request with limited resources
    const authResources = ['https://api.example.com/photos'];
    const authCode = await fixture.completeAuthorizationFlow(authResources);

    // Step 2: Token request with additional resource not in original authorization
    const tokenResources = ['https://api.example.com/photos', 'https://api.example.com/albums']; // 'albums' not in original
    const tokenResponse = await fixture.exchangeAuthCodeForToken(authCode, tokenResources).expect(400);

    // Should reject with invalid_target error
    expect(tokenResponse.body.error).toBe('invalid_target');
    expect(tokenResponse.body.error_description).toContain('not recognized');
  });

  it('should work without resource parameters in token request (use original)', async () => {
    // Step 1: Authorization request with resources
    const authResources = ['https://api.example.com/photos', 'https://api.example.com/albums'];
    const authCode = await fixture.completeAuthorizationFlow(authResources);

    // Step 2: Token request without resource parameters
    const tokenResponse = await fixture.exchangeAuthCodeForTokenWithoutResources(authCode).expect(200);

    // Verify tokens were issued
    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.refresh_token).toBeDefined();

    // Verify access token aud claim contains original resources from authorization
    validateAudienceClaim(tokenResponse.body.access_token, authResources);

    // Verify refresh token orig_resources claim contains original authorization resources
    const refreshTokenDecoded = decodeJwt(tokenResponse.body.refresh_token);
    expect(refreshTokenDecoded.orig_resources).toBeDefined();
    expect(Array.isArray(refreshTokenDecoded.orig_resources)).toBe(true);
    authResources.forEach((resource) => {
      expect(refreshTokenDecoded.orig_resources).toContain(resource);
    });
  });
});
