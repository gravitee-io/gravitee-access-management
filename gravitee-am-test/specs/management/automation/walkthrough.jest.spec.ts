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
 * End-to-end walkthrough test for the Automation API.
 *
 * Simulates a realistic provisioning workflow against the default environment:
 *   1. Create a "customer-auth" domain
 *   2. Add inline + corporate IDPs to the domain
 *   3. Update the domain description
 *   4. Verify idempotency (re-PUT everything)
 *   5. Tear down in reverse order
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { performDelete, performGet, performPut } from '@gateway-commands/oauth-oidc-commands';
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup(120000);

const BASE = () => `${process.env.AM_MANAGEMENT_URL}/management/automation`;
const ORG = () => process.env.AM_DEF_ORG_ID;
const ENV = () => process.env.AM_DEF_ENV_ID;
const envPath = () => `/organizations/${ORG()}/environments/${ENV()}`;

let accessToken: string;
const cleanupDomainIds: string[] = [];

const auth = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${accessToken}`,
});

const inlineConfig = (users: Array<{ username: string; password: string }>) =>
  JSON.stringify({
    users: users.map((u) => ({
      firstname: u.username,
      lastname: 'Test',
      username: u.username,
      password: u.password,
    })),
  });

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
});

afterAll(async () => {
  for (const id of cleanupDomainIds) {
    await safeDeleteDomain(id, accessToken);
  }
});

describe('Walkthrough - Step 1: Create Domain', () => {
  it('should create customer-auth domain', async () => {
    const res = await performPut(
      BASE(),
      `${envPath()}/domains`,
      {
        hrid: 'customer-auth',
        name: 'Customer Authentication',
        description: 'Customer auth domain',
        path: '/default/customer-auth',
        enabled: true,
        dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
      },
      auth(),
    );
    expect(res.status).toBe(200);
    expect(res.body.hrid).toEqual('customer-auth');
    cleanupDomainIds.push(res.body.id);
  });

  it('should retrieve customer-auth domain by HRID', async () => {
    const res = await performGet(BASE(), `${envPath()}/domains/customer-auth`, auth());
    expect(res.status).toBe(200);
    expect(res.body.hrid).toEqual('customer-auth');
    expect(res.body.description).toEqual('Customer auth domain');
  });
});

describe('Walkthrough - Step 2: Add Identity Providers', () => {
  it('should add an inline test-users IDP', async () => {
    const res = await performPut(
      BASE(),
      `${envPath()}/domains/customer-auth/identity-providers`,
      {
        hrid: 'test-users',
        name: 'Test Users',
        type: 'inline-am-idp',
        configuration: inlineConfig([
          { username: 'alice', password: 'P@ssword1' },
          { username: 'bob', password: 'P@ssword1' },
        ]),
      },
      auth(),
    );
    expect(res.status).toBe(200);
    expect(res.body.name).toEqual('Test Users');
    expect(res.body.type).toEqual('inline-am-idp');
  });

  it('should add corporate IDP', async () => {
    const res = await performPut(
      BASE(),
      `${envPath()}/domains/customer-auth/identity-providers`,
      {
        hrid: 'corporate-idp',
        name: 'Corporate Directory',
        type: 'inline-am-idp',
        configuration: inlineConfig([{ username: 'admin', password: 'Admin123!' }]),
      },
      auth(),
    );
    expect(res.status).toBe(200);
    expect(res.body.name).toEqual('Corporate Directory');
  });

  it('should list IDPs for the domain', async () => {
    const res = await performGet(
      BASE(),
      `${envPath()}/domains/customer-auth/identity-providers`,
      auth(),
    );
    expect(res.status).toBe(200);
    const names = res.body.map((idp: any) => idp.name);
    expect(names).toContain('Test Users');
    expect(names).toContain('Corporate Directory');
  });
});

describe('Walkthrough - Step 3: Update the Domain', () => {
  it('should update the domain description via PUT', async () => {
    const res = await performPut(
      BASE(),
      `${envPath()}/domains`,
      {
        hrid: 'customer-auth',
        name: 'Customer Authentication',
        description: 'Updated: now includes social login',
        enabled: true,
      },
      auth(),
    );
    expect(res.status).toBe(200);
    expect(res.body.description).toEqual('Updated: now includes social login');
    expect(res.body.hrid).toEqual('customer-auth');
  });
});

describe('Walkthrough - Step 4: Verify Idempotency', () => {
  it('should re-PUT domain and get same ID', async () => {
    const first = await performPut(
      BASE(),
      `${envPath()}/domains`,
      {
        hrid: 'customer-auth',
        name: 'Customer Authentication',
        description: 'Idempotency check',
        enabled: true,
      },
      auth(),
    );
    const second = await performPut(
      BASE(),
      `${envPath()}/domains`,
      {
        hrid: 'customer-auth',
        name: 'Customer Authentication',
        description: 'Idempotency check',
        enabled: true,
      },
      auth(),
    );
    expect(first.body.id).toEqual(second.body.id);
    expect(first.body.hrid).toEqual(second.body.hrid);
  });

  it('should re-PUT IDP and get same result', async () => {
    const first = await performPut(
      BASE(),
      `${envPath()}/domains/customer-auth/identity-providers`,
      {
        hrid: 'test-users',
        name: 'Test Users',
        type: 'inline-am-idp',
        configuration: inlineConfig([{ username: 'alice', password: 'P@ssword1' }]),
      },
      auth(),
    );
    const second = await performPut(
      BASE(),
      `${envPath()}/domains/customer-auth/identity-providers`,
      {
        hrid: 'test-users',
        name: 'Test Users',
        type: 'inline-am-idp',
        configuration: inlineConfig([{ username: 'alice', password: 'P@ssword1' }]),
      },
      auth(),
    );
    expect(first.body.id).toEqual(second.body.id);
  });
});

describe('Walkthrough - Step 5: Tear Down', () => {
  it('should delete the test-users IDP', async () => {
    const res = await performDelete(
      BASE(),
      `${envPath()}/domains/customer-auth/identity-providers/test-users`,
      auth(),
    );
    expect(res.status).toBe(204);
  });

  it('should delete the corporate IDP', async () => {
    const res = await performDelete(
      BASE(),
      `${envPath()}/domains/customer-auth/identity-providers/corporate-idp`,
      auth(),
    );
    expect(res.status).toBe(204);
  });

  it('should delete the customer-auth domain', async () => {
    const res = await performDelete(BASE(), `${envPath()}/domains/customer-auth`, auth());
    expect(res.status).toBe(204);
  });

  it('should verify the domain is gone', async () => {
    const res = await performGet(BASE(), `${envPath()}/domains/customer-auth`, auth());
    expect(res.status).toBeGreaterThanOrEqual(400);
  });
});
