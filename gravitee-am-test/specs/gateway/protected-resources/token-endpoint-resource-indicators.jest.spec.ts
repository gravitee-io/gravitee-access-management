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
import { afterAll, beforeAll, jest } from '@jest/globals';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import {
  validateSuccessfulTokenResponse,
  validateErrorResponse,
  validateClientIdAudience,
} from './fixtures/test-utils';
import { setupProtectedResourcesFixture, ProtectedResourcesFixture } from './fixtures/protected-resources-fixture';

// RFC 8707 Token Endpoint: resource indicators and audience population

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: ProtectedResourcesFixture;

// Helper function to make token requests
async function makeTokenRequest(resourceParams: string, expectedStatus: number = 200) {
  return await performPost(
    fixture.openIdConfiguration.token_endpoint,
    '',
    `grant_type=client_credentials${resourceParams}`,
    {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.serviceApplication),
    },
  ).expect(expectedStatus);
}

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
      const response = await makeTokenRequest(`&resource=${encodeURIComponent(validResource)}`);

      validateSuccessfulTokenResponse(response, [validResource]);
    });

    it('should issue access token with multiple valid resources', async () => {
      const resources = ['https://api.example.com/photos', 'https://api.example.com/albums'];
      const resourceParams = resources.map((r) => `&resource=${encodeURIComponent(r)}`).join('');
      const response = await makeTokenRequest(resourceParams);

      validateSuccessfulTokenResponse(response, resources);
    });

    it('should work without resource parameter (backward compatibility)', async () => {
      const response = await makeTokenRequest('');

      validateSuccessfulTokenResponse(response);
      validateClientIdAudience(response.body.access_token, fixture.serviceApplication.settings.oauth.clientId);
    });

    it('should deduplicate duplicate resource parameters', async () => {
      const r = 'https://api.example.com/photos';
      const response = await makeTokenRequest(`&resource=${encodeURIComponent(r)}&resource=${encodeURIComponent(r)}`);
      validateSuccessfulTokenResponse(response, [r]);
    });

    it('should accept configured resource with query and fragment', async () => {
      const meta = 'https://api.example.com/meta?foo=bar#frag';
      const response = await makeTokenRequest(`&resource=${encodeURIComponent(meta)}`);
      validateSuccessfulTokenResponse(response, [meta]);
    });
  });

  describe('Invalid Resource Scenarios', () => {
    it('should reject single invalid resource with invalid_target error', async () => {
      const invalidResource = 'https://unknown-api.com/invalid';
      const response = await makeTokenRequest(`&resource=${encodeURIComponent(invalidResource)}`, 400);

      validateErrorResponse(response);
    });

    it('should reject malformed resource URI with invalid_target error', async () => {
      const malformedResource = 'not-a-valid-uri';
      const response = await makeTokenRequest(`&resource=${encodeURIComponent(malformedResource)}`, 400);

      validateErrorResponse(response);
    });

    it('should reject when some resources are invalid (mixed valid/invalid)', async () => {
      const response = await makeTokenRequest(
        `&resource=${encodeURIComponent('https://api.example.com/photos')}&resource=${encodeURIComponent(
          'https://unknown-api.com/invalid',
        )}`,
        400,
      );

      validateErrorResponse(response);
    });
  });
});
