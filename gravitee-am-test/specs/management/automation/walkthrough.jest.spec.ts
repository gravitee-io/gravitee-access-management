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
 *   1. Create a "customer-auth" domain, then its identity providers (separate resource)
 *   2. Update the domain description
 *   3. Verify idempotency (re-PUT everything)
 *   4. Tear down
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { setup } from '../../test-fixture';
import { AutomationClient } from './fixtures/automation-client';
import { buildAutomationDomainDef, buildInlineIdpDef } from './fixtures/automation-definitions';

setup(120000);

const DOMAIN_KEY = uniqueName('customer-auth', true).toLowerCase();
const TEST_USERS_IDP_KEY = uniqueName('test-users', true).toLowerCase();
const CORPORATE_IDP_KEY = uniqueName('corporate-idp', true).toLowerCase();

let accessToken: string;
let client: AutomationClient;
const cleanupDomainKeys: string[] = [];

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toMatch(JWT_FORMAT);
  client = new AutomationClient(accessToken);
});

afterAll(async () => {
  for (const key of cleanupDomainKeys) {
    await client.deleteDomain(key);
  }
});

const domainDefinition = (description: string) =>
  buildAutomationDomainDef({
    key: DOMAIN_KEY,
    name: 'Customer Authentication',
    description,
    path: `/default/${DOMAIN_KEY}`,
    enabled: true,
  });

describe('Walkthrough - Step 1: Create Domain and its Identity Providers', () => {
  it('should create the customer-auth domain', async () => {
    const res = await client.putDomain(domainDefinition('Customer auth domain'));
    expect(res.status).toBe(200);
    expect(res.body.key).toEqual(DOMAIN_KEY);
    cleanupDomainKeys.push(res.body.key);
  });

  it('should create the test-users and corporate identity providers under the domain', async () => {
    const testUsers = await client.putIdentityProvider(
      DOMAIN_KEY,
      buildInlineIdpDef({
        key: TEST_USERS_IDP_KEY,
        name: 'Test Users',
        users: [
          { username: 'alice', password: 'P@ssword1' },
          { username: 'bob', password: 'P@ssword1' },
        ],
      }),
    );
    expect(testUsers.status).toBe(200);

    const corporate = await client.putIdentityProvider(
      DOMAIN_KEY,
      buildInlineIdpDef({
        key: CORPORATE_IDP_KEY,
        name: 'Corporate Directory',
        users: [{ username: 'admin', password: 'Admin123!' }],
      }),
    );
    expect(corporate.status).toBe(200);
  });

  it('should list both identity providers under the domain', async () => {
    const res = await client.listIdentityProviders(DOMAIN_KEY);
    expect(res.status).toBe(200);
    const idpNames = res.body.map((idp: any) => idp.name);
    expect(idpNames).toContain('Test Users');
    expect(idpNames).toContain('Corporate Directory');
  });

  it('should retrieve the customer-auth domain by key', async () => {
    const res = await client.getDomain(DOMAIN_KEY);
    expect(res.status).toBe(200);
    expect(res.body.key).toEqual(DOMAIN_KEY);
    expect(res.body.description).toEqual('Customer auth domain');
  });
});

describe('Walkthrough - Step 2: Update the Domain', () => {
  it('should update the domain description via PUT', async () => {
    const res = await client.putDomain(domainDefinition('Updated: now includes social login'));
    expect(res.status).toBe(200);
    expect(res.body.description).toEqual('Updated: now includes social login');
    expect(res.body.key).toEqual(DOMAIN_KEY);
  });
});

describe('Walkthrough - Step 3: Verify Idempotency', () => {
  it('should re-PUT the domain and get the same key with stable createdAt', async () => {
    const first = await client.putDomain(domainDefinition('Idempotency check'));
    const second = await client.putDomain(domainDefinition('Idempotency check'));

    expect(first.body.key).toEqual(second.body.key);
    expect(first.body.createdAt).toEqual(second.body.createdAt);
  });

  it('should re-PUT an identity provider and preserve its createdAt', async () => {
    const first = await client.getIdentityProvider(DOMAIN_KEY, TEST_USERS_IDP_KEY);
    const second = await client.putIdentityProvider(
      DOMAIN_KEY,
      buildInlineIdpDef({
        key: TEST_USERS_IDP_KEY,
        name: 'Test Users',
        users: [{ username: 'alice', password: 'P@ssword1' }],
      }),
    );

    expect(second.status).toBe(200);
    expect(second.body.createdAt).toEqual(first.body.createdAt);
  });
});

describe('Walkthrough - Step 4: Tear Down', () => {
  it('should delete the customer-auth domain', async () => {
    const res = await client.deleteDomain(DOMAIN_KEY);
    expect(res.status).toBe(204);
    cleanupDomainKeys.splice(cleanupDomainKeys.indexOf(DOMAIN_KEY), 1);
  });

  it('should verify the domain is gone (404)', async () => {
    const res = await client.getDomain(DOMAIN_KEY);
    expect(res.status).toBe(404);
  });
});
