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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { decodeJwt } from '@utils-commands/jwt';
import { validateSuccessfulTokenResponse, validateErrorResponse } from './fixtures/test-utils';
import { setupProtectedResourcesFixture, ProtectedResourcesFixture } from './fixtures/protected-resources-fixture';

// RFC 8707 Token Endpoint: resource indicators and audience population

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: ProtectedResourcesFixture;

// Test utilities
const TokenEndpointTestUtils = {
  // Helper function to validate audience claim contains expected resources
  validateAudienceClaim(token: string, expectedResources: string[]): void {
    const decoded = decodeJwt(token);
    expect(decoded).toBeDefined();

    const aud = decoded.aud;
    expect(aud).toBeDefined();

    // The audience should contain the resource parameters
    if (Array.isArray(aud)) {
      // Should contain exactly the resource parameters
      expectedResources.forEach((resource) => {
        expect(aud).toContain(resource);
      });
    } else {
      // Single audience value - should be one of the resources
      expect(expectedResources).toContain(aud);
    }
  },

  // Helper function to make token requests
  async makeTokenRequest(resourceParams: string, expectedStatus: number = 200) {
    return await performPost(fixture.openIdConfiguration.token_endpoint, '', `grant_type=client_credentials${resourceParams}`, {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.serviceApplication),
    }).expect(expectedStatus);
  },

  // Helper function to validate successful token response
  validateSuccessfulTokenResponse,

  // Helper function to validate error response
  validateErrorResponse,

  // Helper function to validate client_id audience (backward compatibility)
  validateClientIdAudience(token: string): void {
    const decoded = decodeJwt(token);
    expect(decoded).toBeDefined();

    const aud = decoded.aud;
    expect(aud).toBeDefined();

    // When no resource parameter is provided, the audience should be the client_id
    if (Array.isArray(aud)) {
      expect(aud).toContain(fixture.serviceApplication.settings.oauth.clientId);
    } else {
      expect(aud).toBe(fixture.serviceApplication.settings.oauth.clientId);
    }
  },
};

beforeAll(async () => {
  fixture = await setupProtectedResourcesFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

describe('Token Endpoint - Resource Indicators (RFC 8707)', () => {
  describe('Valid Resource Scenarios', () => {
    it('should issue access token with single valid resource', async () => {
      const validResource = 'https://api.example.com/photos';
      const response = await TokenEndpointTestUtils.makeTokenRequest(`&resource=${encodeURIComponent(validResource)}`);

      TokenEndpointTestUtils.validateSuccessfulTokenResponse(response, [validResource]);
    });

    it('should issue access token with multiple valid resources', async () => {
      const resources = ['https://api.example.com/photos', 'https://api.example.com/albums'];
      const resourceParams = resources.map((r) => `&resource=${encodeURIComponent(r)}`).join('');
      const response = await TokenEndpointTestUtils.makeTokenRequest(resourceParams);

      TokenEndpointTestUtils.validateSuccessfulTokenResponse(response, resources);
    });

    it('should work without resource parameter (backward compatibility)', async () => {
      const response = await TokenEndpointTestUtils.makeTokenRequest('');

      TokenEndpointTestUtils.validateSuccessfulTokenResponse(response);
      TokenEndpointTestUtils.validateClientIdAudience(response.body.access_token);
    });

    it('should deduplicate duplicate resource parameters', async () => {
      const r = 'https://api.example.com/photos';
      const response = await TokenEndpointTestUtils.makeTokenRequest(
        `&resource=${encodeURIComponent(r)}&resource=${encodeURIComponent(r)}`,
      );
      TokenEndpointTestUtils.validateSuccessfulTokenResponse(response, [r]);
    });

    it('should accept configured resource with query and fragment', async () => {
      const meta = 'https://api.example.com/meta?foo=bar#frag';
      const response = await TokenEndpointTestUtils.makeTokenRequest(`&resource=${encodeURIComponent(meta)}`);
      TokenEndpointTestUtils.validateSuccessfulTokenResponse(response, [meta]);
    });
  });

  describe('Invalid Resource Scenarios', () => {
    it('should reject single invalid resource with invalid_target error', async () => {
      const invalidResource = 'https://unknown-api.com/invalid';
      const response = await TokenEndpointTestUtils.makeTokenRequest(`&resource=${encodeURIComponent(invalidResource)}`, 400);

      TokenEndpointTestUtils.validateErrorResponse(response);
    });

    it('should reject malformed resource URI with invalid_target error', async () => {
      const malformedResource = 'not-a-valid-uri';
      const response = await TokenEndpointTestUtils.makeTokenRequest(`&resource=${encodeURIComponent(malformedResource)}`, 400);

      TokenEndpointTestUtils.validateErrorResponse(response);
    });

    it('should reject when some resources are invalid (mixed valid/invalid)', async () => {
      const response = await TokenEndpointTestUtils.makeTokenRequest(
        `&resource=${encodeURIComponent('https://api.example.com/photos')}&resource=${encodeURIComponent(
          'https://unknown-api.com/invalid',
        )}`,
        400,
      );

      TokenEndpointTestUtils.validateErrorResponse(response);
    });
  });
});
