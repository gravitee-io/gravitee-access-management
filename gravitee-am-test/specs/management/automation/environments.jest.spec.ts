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
import { beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { performGet, performPut } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';

setup();

const automationUrl = () => `${process.env.AM_MANAGEMENT_URL}/management/automation`;
const orgPath = () => `/organizations/${process.env.AM_DEF_ORG_ID}`;

let accessToken: string;

const authHeaders = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${accessToken}`,
});

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
});

describe('Automation API - Environment - List', () => {
  it('should list environments for the default organization', async () => {
    const response = await performGet(automationUrl(), `${orgPath()}/environments`, authHeaders());

    expect(response.status).toBe(200);
    expect(Array.isArray(response.body)).toBe(true);
    expect(response.body.length).toBeGreaterThanOrEqual(1);

    const defaultEnv = response.body.find((env: any) => env.id === process.env.AM_DEF_ENV_ID);
    expect(defaultEnv).toBeDefined();
    expect(defaultEnv.name).toEqual('Default environment');
  });
});

describe('Automation API - Environment - Get', () => {
  it('should get the default environment by ID', async () => {
    const response = await performGet(
      automationUrl(),
      `${orgPath()}/environments/${process.env.AM_DEF_ENV_ID}`,
      authHeaders(),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(process.env.AM_DEF_ENV_ID);
    expect(response.body.name).toEqual('Default environment');
    expect(response.body.organizationId).toEqual(process.env.AM_DEF_ORG_ID);
  });

  it('should return error for non-existent environment', async () => {
    const response = await performGet(automationUrl(), `${orgPath()}/environments/non-existent-env`, authHeaders());

    expect(response.status).toBeGreaterThanOrEqual(400);
  });
});

describe('Automation API - Environment - PUT on collection (Idempotent Create/Update)', () => {
  it('should update the default environment via PUT to collection', async () => {
    const definition = {
      hrid: 'default',
      name: 'Default environment',
      description: 'Updated via Automation API',
    };

    const response = await performPut(automationUrl(), `${orgPath()}/environments`, definition, authHeaders());

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual('Default environment');
    expect(response.body.description).toEqual('Updated via Automation API');
  });

  it('should be idempotent - second PUT returns same result', async () => {
    const definition = {
      hrid: 'default',
      name: 'Default environment',
      description: 'Idempotent check via Automation API',
    };

    const first = await performPut(automationUrl(), `${orgPath()}/environments`, definition, authHeaders());
    const second = await performPut(automationUrl(), `${orgPath()}/environments`, definition, authHeaders());

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(first.body.id).toEqual(second.body.id);
    expect(first.body.description).toEqual(second.body.description);
  });
});

describe('Automation API - Environment - Authentication', () => {
  it('should return 401 when no Authorization header is provided', async () => {
    const response = await performGet(automationUrl(), `${orgPath()}/environments`, {
      'Content-Type': 'application/json',
    });

    expect(response.status).toBe(401);
  });

  it('should return 401 when an invalid Bearer token is provided', async () => {
    const response = await performGet(automationUrl(), `${orgPath()}/environments`, {
      'Content-Type': 'application/json',
      Authorization: 'Bearer invalid-token-value',
    });

    expect(response.status).toBe(401);
  });

  it('should return 401 for PUT without authentication', async () => {
    const definition = {
      hrid: 'default',
      name: 'Default environment',
      description: 'Should fail',
    };

    const response = await performPut(automationUrl(), `${orgPath()}/environments`, definition, {
      'Content-Type': 'application/json',
    });

    expect(response.status).toBe(401);
  });
});
