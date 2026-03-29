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

setup(60000);

const automationUrl = () => `${process.env.AM_MANAGEMENT_URL}/management/automation`;
const envPath = () => `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}`;

let accessToken: string;
let testDomainId: string;
let testDomainHrid: string;
const testDomainName = uniqueName('autoidp', true);

const authHeaders = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${accessToken}`,
});

const inlineIdpConfig = (username: string, password: string) =>
  JSON.stringify({
    users: [
      {
        firstname: 'Test',
        lastname: 'User',
        username,
        password,
      },
    ],
  });

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // Create a test domain via PUT on collection
  const domainDef = {
    hrid: testDomainName,
    name: `IDP Test Domain ${testDomainName}`,
    description: 'Domain for IDP automation tests',
    dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
  };
  const domainResponse = await performPut(automationUrl(), `${envPath()}/domains`, domainDef, authHeaders());
  expect(domainResponse.status).toBe(200);
  testDomainId = domainResponse.body.id;
  testDomainHrid = domainResponse.body.hrid;
});

afterAll(async () => {
  if (testDomainId) {
    await safeDeleteDomain(testDomainId, accessToken);
  }
});

describe('Automation API - Identity Provider - List', () => {
  it('should list identity providers for the test domain', async () => {
    const response = await performGet(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers`,
      authHeaders(),
    );

    expect(response.status).toBe(200);
    expect(Array.isArray(response.body)).toBe(true);
  });
});

describe('Automation API - Identity Provider - PUT on collection (Idempotent Create/Update)', () => {
  const idpHrid = uniqueName('autoinline', true);

  it('should create a new inline identity provider via PUT to collection', async () => {
    const definition = {
      hrid: idpHrid,
      name: `Automation IDP ${idpHrid}`,
      type: 'inline-am-idp',
      configuration: inlineIdpConfig('testuser', 'Password1!'),
    };

    const response = await performPut(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers`,
      definition,
      authHeaders(),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toBeDefined();
    expect(response.body.name).toEqual(`Automation IDP ${idpHrid}`);
    expect(response.body.type).toEqual('inline-am-idp');
  });

  it('should retrieve the created identity provider by hrid via path', async () => {
    const response = await performGet(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers/${idpHrid}`,
      authHeaders(),
    );

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual(`Automation IDP ${idpHrid}`);
  });

  it('should update identity provider via second PUT to collection (idempotent)', async () => {
    const definition = {
      hrid: idpHrid,
      name: `Automation IDP ${idpHrid} Updated`,
      type: 'inline-am-idp',
      configuration: inlineIdpConfig('testuser2', 'Password2!'),
    };

    const response = await performPut(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers`,
      definition,
      authHeaders(),
    );

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual(`Automation IDP ${idpHrid} Updated`);
  });

  it('should appear in the identity provider list', async () => {
    const response = await performGet(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers`,
      authHeaders(),
    );

    expect(response.status).toBe(200);
    const found = response.body.find((idp: any) => idp.name === `Automation IDP ${idpHrid} Updated`);
    expect(found).toBeDefined();
  });
});

describe('Automation API - Identity Provider - DELETE via path', () => {
  const deleteIdpHrid = uniqueName('autodelid', true);

  it('should create then delete an identity provider', async () => {
    const definition = {
      hrid: deleteIdpHrid,
      name: `Delete IDP ${deleteIdpHrid}`,
      type: 'inline-am-idp',
      configuration: inlineIdpConfig('deluser', 'Password1!'),
    };

    const createResponse = await performPut(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers`,
      definition,
      authHeaders(),
    );
    expect(createResponse.status).toBe(200);

    const deleteResponse = await performDelete(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers/${deleteIdpHrid}`,
      authHeaders(),
    );
    expect(deleteResponse.status).toBe(204);

    const getResponse = await performGet(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers/${deleteIdpHrid}`,
      authHeaders(),
    );
    expect(getResponse.status).toBeGreaterThanOrEqual(400);
  });
});

describe('Automation API - Identity Provider - Error Handling', () => {
  it('should return error for non-existent IDP', async () => {
    const response = await performGet(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers/does-not-exist-xyz`,
      authHeaders(),
    );

    expect(response.status).toBeGreaterThanOrEqual(400);
  });

  it('should reject PUT with missing required type', async () => {
    const response = await performPut(
      automationUrl(),
      `${envPath()}/domains/${testDomainHrid}/identity-providers`,
      { hrid: 'bad', name: 'Missing type field' },
      authHeaders(),
    );

    expect(response.status).toBeGreaterThanOrEqual(400);
  });
});
