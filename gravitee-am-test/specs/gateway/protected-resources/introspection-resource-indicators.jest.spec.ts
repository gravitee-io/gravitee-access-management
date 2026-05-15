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
import {
  MULTI_AUD_RESOURCE_IDENTIFIERS,
  ProtectedResourcesFixture,
  setupProtectedResourcesFixture,
} from './fixtures/protected-resources-fixture';
import { delay } from '@utils-commands/misc';
import { requestClientCredentialsToken } from '@gateway-commands/oauth-oidc-commands';

// RFC 8707 Introspection: Protected Resource can introspect tokens obtained via authorization_code grant with resource indicators

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

describe('Protected Resource Introspection with Resource Indicators (RFC 8707)', () => {
  it('Protected Resource can introspect token obtained via authorization_code grant', async () => {
    // Use resources from the fixture's protected resources
    const resources = ['https://api.example.com/photos'];

    // Step 1: Complete authorization flow with resources to get authorization code
    const authCode = await fixture.completeAuthorizationFlow(resources);
    expect(authCode).toBeDefined();
    expect(authCode.length).toBeGreaterThan(0);

    // Step 2: Exchange authorization code for token with resources
    const tokenResponse = await fixture.exchangeAuthCodeForToken(authCode, resources).expect(200);
    expect(tokenResponse.body.access_token).toBeDefined();
    const accessToken = tokenResponse.body.access_token;

    // Step 3: Use one of the protected resources to introspect the token
    const protectedResource = fixture.protectedResources[0];
    expect(protectedResource).toBeDefined();
    expect(protectedResource.clientId).toBeDefined();
    expect(protectedResource.clientSecret).toBeDefined();

    // Add a 10 second delay to ensure offline verification is not conducted
    await delay(10000);

    // Step 4: Introspect the token using the Protected Resource credentials
    // Note that the client ID of the resources indicated in the token exchange must match the client ID of the Authorization header
    const introspectionResponse = await fixture.introspectToken(accessToken, protectedResource).expect(200);

    // Verify introspection response
    expect(introspectionResponse.body).toBeDefined();
    expect(introspectionResponse.body.active).toBe(true);
    expect(introspectionResponse.body.client_id).toBe('protected-resources-client');
    expect(introspectionResponse.body.aud).toBe(resources[0]);
    expect(introspectionResponse.body.token_type).toBe('bearer');
    expect(introspectionResponse.body.scope).toBe('openid');
    expect(introspectionResponse.body.username).toBe('protecteduser');
    expect(introspectionResponse.body.sub).toBeDefined();
    expect(typeof introspectionResponse.body.sub).toBe('string');
    expect(introspectionResponse.body.iss).toBeDefined();
    expect(introspectionResponse.body.iss).toContain(fixture.domain.hrid);
    expect(introspectionResponse.body.domain).toBe(fixture.domain.id);
    expect(introspectionResponse.body.jti).toBeDefined();
    expect(typeof introspectionResponse.body.jti).toBe('string');
    expect(introspectionResponse.body.exp).toBeDefined();
    expect(introspectionResponse.body.iat).toBeDefined();
    expect(introspectionResponse.body.exp).toBeGreaterThan(introspectionResponse.body.iat);
  });

  it('Protected Resource can introspect token with multiple aud values (RFC 8707 multi-resource)', async () => {
    const resourceIdentifiers = [...MULTI_AUD_RESOURCE_IDENTIFIERS];
    const multiAudResource = fixture.protectedResources.find((r) => r.name === 'Multi-Audience API');
    expect(multiAudResource).toBeDefined();
    expect(multiAudResource.clientId).toBeDefined();
    expect(multiAudResource.clientSecret).toBeDefined();

    // Mint a token via client_credentials carrying both resources -> aud is a 2-element array.
    const tokenResponse = await fixture.requestClientCredentialsToken(resourceIdentifiers).expect(200);
    expect(tokenResponse.body.access_token).toBeDefined();

    // No offline-verification wait: exercise with the offline path
    const introspectionResponse = await fixture
    .introspectToken(tokenResponse.body.access_token, multiAudResource)
    .expect(200);

    expect(introspectionResponse.body.active).toBe(true);
    expect(Array.isArray(introspectionResponse.body.aud)).toBe(true);
    expect(introspectionResponse.body.aud).toEqual(expect.arrayContaining(resourceIdentifiers));
  });
});

