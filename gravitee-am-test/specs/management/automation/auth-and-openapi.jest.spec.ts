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

/**
 * Asserts the existing cross-cutting Automation API functionality:
 *  - stateless bearer-JWT authentication (401 for missing / invalid tokens)
 *  - the OpenAPI specification endpoints (served unauthenticated, automation-only)
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import {
  AutomationAuthFixture,
  setupAutomationAuthFixture,
} from './fixtures/automation-domain-fixture';
import { authHeaders, automationUrl, envPath, jsonHeaders } from './fixtures/automation-client';
import { performGet } from '@gateway-commands/oauth-oidc-commands';

setup();

let fixture: AutomationAuthFixture;

beforeAll(async () => {
  fixture = await setupAutomationAuthFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - Authentication (stateless bearer JWT)', () => {
  it('should reject a request with no Authorization header (401)', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains`, jsonHeaders());
    expect(response.status).toBe(401);
  });

  it('should reject a request with a malformed bearer token (401)', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains`, jsonHeaders({ Authorization: 'Bearer not-a-real-jwt' }));
    expect(response.status).toBe(401);
  });

  it('should accept a request with a valid admin bearer JWT (200)', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains`, authHeaders(fixture.accessToken));
    expect(response.status).toBe(200);
  });
});

describe('Automation API - OpenAPI specification', () => {
  it('should serve /openapi.json without authentication', async () => {
    const response = await performGet(automationUrl(), '/openapi.json', jsonHeaders());
    expect(response.status).toBe(200);

    const spec = response.body;
    expect(spec.openapi).toEqual(expect.stringMatching(/^3\./));
    expect(spec.info.title).toContain('Automation API');

    // automation-only tags
    const tagNames = (spec.tags || []).map((t: any) => t.name);
    expect(tagNames).toEqual(expect.arrayContaining(['Domains', 'Identity Providers']));

    // automation-only paths, no inherited Management API paths
    const paths = Object.keys(spec.paths || {});
    expect(paths.length).toBeGreaterThan(0);
    expect(paths.every((p) => p.startsWith('/organizations/{orgId}'))).toBe(true);
    // identity providers are a resource under a domain
    expect(paths.some((p) => p.endsWith('/identity-providers'))).toBe(true);

    // automation-specific security schemes only (no inherited gravitee-auth)
    const schemes = Object.keys(spec.components?.securitySchemes || {});
    expect(schemes).toEqual(expect.arrayContaining(['BearerAuth', 'BasicAuth']));
    expect(schemes).not.toContain('gravitee-auth');
  });

  it('should serve /openapi.yaml without authentication', async () => {
    const response = await performGet(automationUrl(), '/openapi.yaml', {});
    expect(response.status).toBe(200);
    const body = response.text || JSON.stringify(response.body);
    expect(body).toContain('openapi');
    expect(body).toContain('Automation API');
  });
});
