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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { performDelete, performGet, performPut } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup();

const automationUrl = () => `${process.env.AM_MANAGEMENT_URL}/management/automation`;
const envPath = () => `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}`;

let accessToken: string;
const createdDomainIds: string[] = [];

const authHeaders = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${accessToken}`,
});

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
});

afterAll(async () => {
  for (const domainId of createdDomainIds) {
    await safeDeleteDomain(domainId, accessToken);
  }
});

describe('Automation API - Domain - List', () => {
  it('should list domains for the default environment', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains`, authHeaders());

    expect(response.status).toBe(200);
    expect(Array.isArray(response.body)).toBe(true);
  });
});

describe('Automation API - Domain - PUT on collection (Idempotent Create/Update)', () => {
  const domainHrid = uniqueName('autodom', true);

  it('should create a new domain via PUT to collection with hrid in body', async () => {
    const definition = {
      hrid: domainHrid,
      name: `Automation Domain ${domainHrid}`,
      description: 'Created via Automation API',
      enabled: true,
      dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
    };

    const response = await performPut(automationUrl(), `${envPath()}/domains`, definition, authHeaders());

    expect(response.status).toBe(200);
    expect(response.body.id).toBeDefined();
    expect(response.body.hrid).toEqual(domainHrid);
    expect(response.body.name).toEqual(`Automation Domain ${domainHrid}`);
    expect(response.body.description).toEqual('Created via Automation API');
    createdDomainIds.push(response.body.id);
  });

  it('should produce a deterministic UUID id', async () => {
    const id = createdDomainIds[createdDomainIds.length - 1];
    expect(id).toBeDefined();
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });

  it('should retrieve the created domain by HRID via path', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains/${domainHrid}`, authHeaders());

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual(`Automation Domain ${domainHrid}`);
  });

  it('should update the domain via second PUT to collection (idempotent)', async () => {
    const definition = {
      hrid: domainHrid,
      name: `Automation Domain ${domainHrid}`,
      description: 'Updated via Automation API',
      enabled: true,
    };

    const response = await performPut(automationUrl(), `${envPath()}/domains`, definition, authHeaders());

    expect(response.status).toBe(200);
    expect(response.body.description).toEqual('Updated via Automation API');
    expect(createdDomainIds).toContain(response.body.id);
  });

  it('should appear in the domain list', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains`, authHeaders());

    expect(response.status).toBe(200);
    const found = response.body.find((d: any) => d.hrid === domainHrid);
    expect(found).toBeDefined();
  });
});

describe('Automation API - Domain - DELETE via path', () => {
  const deleteHrid = uniqueName('autodel', true);

  it('should create then delete a domain', async () => {
    const definition = {
      hrid: deleteHrid,
      name: `Delete Me ${deleteHrid}`,
      description: 'To be deleted',
      dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
    };

    const createResponse = await performPut(automationUrl(), `${envPath()}/domains`, definition, authHeaders());
    expect(createResponse.status).toBe(200);
    expect(createResponse.body.hrid).toEqual(deleteHrid);

    const deleteResponse = await performDelete(automationUrl(), `${envPath()}/domains/${deleteHrid}`, authHeaders());
    expect(deleteResponse.status).toBe(204);

    const getResponse = await performGet(automationUrl(), `${envPath()}/domains/${deleteHrid}`, authHeaders());
    expect(getResponse.status).toBeGreaterThanOrEqual(400);
  });
});

describe('Automation API - Domain - Error Handling', () => {
  it('should return error for non-existent domain HRID', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains/does-not-exist-xyz`, authHeaders());

    expect(response.status).toBeGreaterThanOrEqual(400);
  });

  it('should reject PUT with missing required hrid', async () => {
    const response = await performPut(
      automationUrl(),
      `${envPath()}/domains`,
      { name: 'Missing hrid field' },
      authHeaders(),
    );

    expect(response.status).toBeGreaterThanOrEqual(400);
  });
});
