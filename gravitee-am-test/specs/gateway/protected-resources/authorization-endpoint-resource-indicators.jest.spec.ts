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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { ProtectedResourcesFixture, setupProtectedResourcesFixture } from './fixtures/protected-resources-fixture';
import { buildAuthorizationUrlWithResources } from './fixtures/protected-resources-fixture';

// RFC 8707 Authorization Endpoint: resource indicators handling and error redirects

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: ProtectedResourcesFixture;

beforeAll(async () => {
  fixture = await setupProtectedResourcesFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

describe('Authorization Endpoint - Resource Indicators (RFC 8707)', () => {

  it('should accept valid resource and redirect to login', async () => {
    const validResource = 'https://api.example.com/photos';
    const url = buildAuthorizationUrlWithResources(
      fixture.openIdConfiguration.authorization_endpoint,
      fixture.application.settings.oauth.clientId,
      fixture.redirectUri,
      [validResource]
    );
    const response = await performGet(url).expect(302);

    // Should redirect to login page (success case)
    expect(response.headers.location).toBeDefined();
    const location = response.headers.location;

    // Should NOT contain error parameters (error, error_description)
    expect(location).not.toContain('error=invalid_target');
    expect(location).not.toContain('error_description');

    // Should redirect to login page (not error page)
    expect(location).toContain('/login');
  });


  it('should accept multiple valid resources and redirect to login', async () => {
    const url = buildAuthorizationUrlWithResources(
      fixture.openIdConfiguration.authorization_endpoint,
      fixture.application.settings.oauth.clientId,
      fixture.redirectUri,
      ['https://api.example.com/photos', 'https://api.example.com/albums']
    );
    const response = await performGet(url).expect(302);

    // Should redirect to login page (success case)
    expect(response.headers.location).toBeDefined();
    const location = response.headers.location;

    // Should NOT contain error parameters
    expect(location).not.toContain('error=invalid_target');
    expect(location).not.toContain('error_description');

    // Should redirect to login page (not error page)
    expect(location).toContain('/login');
  });


  it('should work without resource parameter (backward compatibility)', async () => {
    const response = await performGet(
      `${fixture.openIdConfiguration.authorization_endpoint}?response_type=code&client_id=${fixture.application.settings.oauth.clientId}&redirect_uri=${encodeURIComponent(fixture.redirectUri)}`
    ).expect(302);

    // Should redirect to login page (backward compatibility)
    expect(response.headers.location).toBeDefined();
    const location = response.headers.location;

    // Should NOT contain error parameters
    expect(location).not.toContain('error=invalid_target');
    expect(location).not.toContain('error_description');

    // Should redirect to login page (not error page)
    expect(location).toContain('/login');
  });

  it('should reject invalid resource with error redirect', async () => {
    const invalidResource = 'https://unknown-api.com/invalid';
    const url = buildAuthorizationUrlWithResources(
      fixture.openIdConfiguration.authorization_endpoint,
      fixture.application.settings.oauth.clientId,
      fixture.redirectUri,
      [invalidResource]
    );
    const response = await performGet(url).expect(302);

    // Should redirect to error page
    expect(response.headers.location).toBeDefined();
    const location = response.headers.location;

    // Should contain error parameters
    expect(location).toContain('error=invalid_target');
    expect(location).toContain('error_description');
    expect(location).toContain('not+recognized');
  });

  it('should reject malformed resource URI with error redirect', async () => {
    const malformedResource = 'not-a-valid-uri';
    const url = buildAuthorizationUrlWithResources(
      fixture.openIdConfiguration.authorization_endpoint,
      fixture.application.settings.oauth.clientId,
      fixture.redirectUri,
      [malformedResource]
    );
    const response = await performGet(url).expect(302);

    // Should redirect to error page
    expect(response.headers.location).toBeDefined();
    const location = response.headers.location;

    // Should contain error parameters
    expect(location).toContain('error=invalid_target');
    expect(location).toContain('error_description');
    expect(location).toContain('not+recognized');
  });

  it('should reject mixed valid and invalid resources with error redirect', async () => {
    const url = buildAuthorizationUrlWithResources(
      fixture.openIdConfiguration.authorization_endpoint,
      fixture.application.settings.oauth.clientId,
      fixture.redirectUri,
      ['https://api.example.com/photos', 'https://unknown-api.com/invalid']
    );
    const response = await performGet(url).expect(302);

    // Should redirect to error page (any invalid resource should cause error)
    expect(response.headers.location).toBeDefined();
    const location = response.headers.location;

    // Should contain error parameters
    expect(location).toContain('error=invalid_target');
    expect(location).toContain('error_description');
    expect(location).toContain('not+recognized');
  });

  it('should reject empty resource parameter with error redirect', async () => {
    const response = await performGet(
      `${fixture.openIdConfiguration.authorization_endpoint}?response_type=code&client_id=${fixture.application.settings.oauth.clientId}&redirect_uri=${encodeURIComponent(fixture.redirectUri)}&resource=`
    ).expect(302);

    // Should redirect to error page (empty resource is treated as invalid)
    expect(response.headers.location).toBeDefined();
    const location = response.headers.location;

    // Should contain error parameters
    expect(location).toContain('error=invalid_target');
    expect(location).toContain('error_description');
    expect(location).toContain('not+recognized');
  });
});
