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
 * Simulates a realistic provisioning workflow:
 *   1. Create dev + staging environments
 *   2. Create a "customer-auth" domain in each
 *   3. Add an inline IDP to the dev domain
 *   4. Add an LDAP IDP to both domains
 *   5. Update the dev domain description
 *   6. Verify idempotency (re-PUT everything)
 *   7. Tear down in reverse order
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { performDelete, performGet, performPut } from '@gateway-commands/oauth-oidc-commands';
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup(120000);

const BASE = () => `${process.env.AM_MANAGEMENT_URL}/management/automation`;
const ORG = () => process.env.AM_DEF_ORG_ID;
const orgPath = () => `/organizations/${ORG()}`;

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

describe('Walkthrough - Step 1: Provision Environments', () => {
  it('should create a dev environment', async () => {
    const res = await performPut(
      BASE(),
      `${orgPath()}/environments`,
      { hrid: 'wt-dev', name: 'Walkthrough Dev', description: 'Dev environment for walkthrough test' },
      auth(),
    );
    expect(res.status).toBe(200);
    expect(res.body.name).toEqual('Walkthrough Dev');
  });

  it('should create a staging environment', async () => {
    const res = await performPut(
      BASE(),
      `${orgPath()}/environments`,
      { hrid: 'wt-staging', name: 'Walkthrough Staging', description: 'Staging environment for walkthrough test' },
      auth(),
    );
    expect(res.status).toBe(200);
    expect(res.body.name).toEqual('Walkthrough Staging');
  });

  it('should list both new environments', async () => {
    const res = await performGet(BASE(), `${orgPath()}/environments`, auth());
    expect(res.status).toBe(200);
    const names = res.body.map((e: any) => e.name);
    expect(names).toContain('Walkthrough Dev');
    expect(names).toContain('Walkthrough Staging');
  });
});

describe('Walkthrough - Step 2: Create Domains', () => {
  for (const env of ['wt-dev', 'wt-staging']) {
    it(`should create customer-auth domain in ${env}`, async () => {
      const res = await performPut(
        BASE(),
        `${orgPath()}/environments/${env}/domains`,
        {
          hrid: 'customer-auth',
          name: 'Customer Authentication',
          description: `Customer auth for ${env}`,
          path: `/${env}/customer-auth`,
          enabled: true,
          dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
        },
        auth(),
      );
      expect(res.status).toBe(200);
      expect(res.body.hrid).toEqual('customer-auth');
      cleanupDomainIds.push(res.body.id);
    });
  }

  it('should retrieve dev customer-auth domain by HRID', async () => {
    const res = await performGet(BASE(), `${orgPath()}/environments/wt-dev/domains/customer-auth`, auth());
    expect(res.status).toBe(200);
    expect(res.body.hrid).toEqual('customer-auth');
    expect(res.body.description).toEqual('Customer auth for wt-dev');
  });
});

describe('Walkthrough - Step 3: Add Identity Providers', () => {
  it('should add an inline test-users IDP to dev domain', async () => {
    const res = await performPut(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains/customer-auth/identity-providers`,
      {
        hrid: 'test-users',
        name: 'Dev Test Users',
        type: 'inline-am-idp',
        configuration: inlineConfig([
          { username: 'alice', password: 'P@ssword1' },
          { username: 'bob', password: 'P@ssword1' },
        ]),
      },
      auth(),
    );
    expect(res.status).toBe(200);
    expect(res.body.name).toEqual('Dev Test Users');
    expect(res.body.type).toEqual('inline-am-idp');
  });

  it('should add corporate IDP to dev', async () => {
    const res = await performPut(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains/customer-auth/identity-providers`,
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

  it('should add corporate IDP to staging', async () => {
    const res = await performPut(
      BASE(),
      `${orgPath()}/environments/wt-staging/domains/customer-auth/identity-providers`,
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

  it('should list IDPs for dev domain', async () => {
    const res = await performGet(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains/customer-auth/identity-providers`,
      auth(),
    );
    expect(res.status).toBe(200);
    const names = res.body.map((idp: any) => idp.name);
    expect(names).toContain('Dev Test Users');
    expect(names).toContain('Corporate Directory');
  });
});

describe('Walkthrough - Step 4: Update a Domain', () => {
  it('should update the dev domain description via PUT', async () => {
    const res = await performPut(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains`,
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

describe('Walkthrough - Step 5: Verify Idempotency', () => {
  it('should re-PUT dev domain and get same ID', async () => {
    const first = await performPut(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains`,
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
      `${orgPath()}/environments/wt-dev/domains`,
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
      `${orgPath()}/environments/wt-dev/domains/customer-auth/identity-providers`,
      {
        hrid: 'test-users',
        name: 'Dev Test Users',
        type: 'inline-am-idp',
        configuration: inlineConfig([{ username: 'alice', password: 'P@ssword1' }]),
      },
      auth(),
    );
    const second = await performPut(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains/customer-auth/identity-providers`,
      {
        hrid: 'test-users',
        name: 'Dev Test Users',
        type: 'inline-am-idp',
        configuration: inlineConfig([{ username: 'alice', password: 'P@ssword1' }]),
      },
      auth(),
    );
    expect(first.body.id).toEqual(second.body.id);
  });
});

describe('Walkthrough - Step 6: Tear Down', () => {
  it('should delete the test-users IDP from dev', async () => {
    const res = await performDelete(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains/customer-auth/identity-providers/test-users`,
      auth(),
    );
    expect(res.status).toBe(204);
  });

  it('should delete the corporate IDP from dev', async () => {
    const res = await performDelete(
      BASE(),
      `${orgPath()}/environments/wt-dev/domains/customer-auth/identity-providers/corporate-idp`,
      auth(),
    );
    expect(res.status).toBe(204);
  });

  it('should delete the corporate IDP from staging', async () => {
    const res = await performDelete(
      BASE(),
      `${orgPath()}/environments/wt-staging/domains/customer-auth/identity-providers/corporate-idp`,
      auth(),
    );
    expect(res.status).toBe(204);
  });

  it('should delete the staging customer-auth domain', async () => {
    const res = await performDelete(BASE(), `${orgPath()}/environments/wt-staging/domains/customer-auth`, auth());
    expect(res.status).toBe(204);
  });

  it('should delete the dev customer-auth domain', async () => {
    const res = await performDelete(BASE(), `${orgPath()}/environments/wt-dev/domains/customer-auth`, auth());
    expect(res.status).toBe(204);
  });

  it('should verify dev domain is gone', async () => {
    const res = await performGet(BASE(), `${orgPath()}/environments/wt-dev/domains/customer-auth`, auth());
    expect(res.status).toBeGreaterThanOrEqual(400);
  });
});
