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

import { afterEach, beforeAll, describe, expect, it } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import {
  AutomationAuthFixture,
  setupAutomationAuthFixture,
} from './fixtures/automation-domain-fixture';
import { AutomationDomainDef, buildAutomationDomainDef } from './fixtures/automation-definitions';

setup(120000);

const ISO_8601_UTC = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;

let fixture: AutomationAuthFixture;

beforeAll(async () => {
  fixture = await setupAutomationAuthFixture();
});

const createdDomainKeys: string[] = [];

afterEach(async () => {
  while (createdDomainKeys.length) {
    await fixture.client.deleteDomain(createdDomainKeys.pop());
  }
});

/** PUT a domain on the collection and track its key for cleanup. */
async function createDomain(overrides: Partial<AutomationDomainDef> & { key?: string } = {}) {
  const key = overrides.key ?? uniqueName('autodom', true).toLowerCase();
  createdDomainKeys.push(key);
  const response = await fixture.client.putDomain(buildAutomationDomainDef({ ...overrides, key }));
  return { key, response };
}

// A definition exercising every settings block we surface, parameterised by key.
const fullDef = (key: string) =>
  buildAutomationDomainDef({
    key,
    name: `Round-trip ${key}`,
    description: 'covers every settings block we surface',
    enabled: false, // diverges from the build* default; must survive the round-trip
    master: true,
    alertEnabled: true,
    tags: ['alpha', 'beta'],
    vhostMode: true,
    vhosts: [{ host: 'auth.example.com', path: `/${key}`, overrideEntrypoint: true }],
    oidc: {
      redirectUriStrictMatching: true,
      postLogoutRedirectUris: ['https://app.example.com/post-logout'],
      requestUris: ['https://app.example.com/request'],
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
        dynamicClientRegistrationEnabled: true,
        openDynamicClientRegistrationEnabled: true,
        clientTemplateEnabled: true,
        allowedScopesEnabled: true,
      },
      securityProfileSettings: {
        enablePlainFapi: true,
      },
      workloadIdentitySettings: {
        enabled: true,
        allowUnsecuredHttpUri: true,
      },
    },
    loginSettings: {
      inherited: false,
      registerEnabled: true,
      forgotPasswordEnabled: true,
      rememberMeEnabled: true,
      hideForm: false,
    },
    accountSettings: {
      inherited: false,
      loginAttemptsDetectionEnabled: true,
      maxLoginAttempts: 5,
      loginAttemptsResetTime: 600,
      rememberMe: true,
      rememberMeDuration: 86400,
    },
    passwordSettings: {
      inherited: false,
      minLength: 12,
      maxLength: 64,
      includeNumbers: true,
      includeSpecialCharacters: true,
      lettersInMixedCase: true,
    },
    corsSettings: {
      enabled: true,
      allowedOrigins: ['https://app.example.com'],
      allowedMethods: ['GET', 'POST'],
      allowedHeaders: ['Authorization', 'Content-Type'],
      allowCredentials: true,
      maxAge: 3600,
    },
    scim: { enabled: true },
    selfServiceAccountManagementSettings: { enabled: true },
  });

/** Create a domain from {@link fullDef} and track its key for cleanup. */
async function createFullDomain(key = uniqueName('autorr', true).toLowerCase()) {
  createdDomainKeys.push(key);
  const response = await fixture.client.putDomain(fullDef(key));
  return { key, response };
}

describe('Automation API - Domain - List', () => {
  it('should list domains for the default environment', async () => {
    const response = await fixture.client.listDomains();

    expect(response.status).toBe(200);
    expect(Array.isArray(response.body)).toBe(true);
  });
});

describe('Automation API - Domain - PUT on collection (Idempotent Create/Update)', () => {
  it('should create a new domain via PUT to collection with key in body', async () => {
    const { key, response } = await createDomain({ enabled: true });

    expect(response.status).toBe(200);
    // Internal id is intentionally not exposed — the Automation API is key-only.
    expect(response.body.id).toBeUndefined();
    expect(response.body.key).toEqual(key);
    expect(response.body.name).toEqual(`Automation Domain ${key}`);
    expect(response.body.description).toEqual('Created via Automation API');
  });

  it('should be idempotent on a subsequent PUT (createdAt unchanged)', async () => {
    const { key, response: created } = await createDomain({ enabled: true });
    expect(created.status).toBe(200);

    const response = await fixture.client.putDomain(buildAutomationDomainDef({ key, enabled: true }));

    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    // createdAt is stable across an update; a duplicate create would reset it
    expect(response.body.createdAt).toEqual(created.body.createdAt);
    expect(typeof response.body.createdAt).toBe('string');
    expect(response.body.createdAt).toMatch(ISO_8601_UTC);
    expect(response.body.updatedAt).toMatch(ISO_8601_UTC);
  });

  it('should retrieve the created domain by key via path', async () => {
    const { key } = await createDomain({ enabled: true });

    const response = await fixture.client.getDomain(key);

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual(`Automation Domain ${key}`);
  });

  it('should update the domain via second PUT to collection (idempotent)', async () => {
    const { key } = await createDomain({ enabled: true });

    const response = await fixture.client.putDomain(
      buildAutomationDomainDef({ key, description: 'Updated via Automation API', enabled: true }),
    );

    expect(response.status).toBe(200);
    expect(response.body.description).toEqual('Updated via Automation API');
    expect(response.body.key).toEqual(key);
  });

  it('should appear in the domain list', async () => {
    const { key } = await createDomain({ enabled: true });

    const response = await fixture.client.listDomains();

    expect(response.status).toBe(200);
    const found = response.body.find((d: any) => d.key === key);
    expect(found).toEqual(expect.objectContaining({ key }));
  });
});

describe('Automation API - Domain - DELETE via path', () => {
  it('should create then delete a domain', async () => {
    const deleteKey = uniqueName('autodel', true).toLowerCase();
    const createResponse = await fixture.client.putDomain(
      buildAutomationDomainDef({ key: deleteKey, name: `Delete Me ${deleteKey}`, description: 'To be deleted' }),
    );
    expect(createResponse.status).toBe(200);
    expect(createResponse.body.key).toEqual(deleteKey);

    const deleteResponse = await fixture.client.deleteDomain(deleteKey);
    expect(deleteResponse.status).toBe(204);

    const getResponse = await fixture.client.getDomain(deleteKey);
    expect(getResponse.status).toBe(404);
  });
});

describe('Automation API - Domain - Error Handling', () => {
  it('should return 404 for a non-existent domain key', async () => {
    const response = await fixture.client.getDomain('does-not-exist-xyz');

    expect(response.status).toBe(404);
  });

  it('should reject PUT with missing required key (400)', async () => {
    const response = await fixture.client.putDomain({ name: 'Missing key field' });

    expect(response.status).toBe(400);
  });
});

describe('Automation API - Domain - Round-trip preserves all writable fields', () => {
  it('should create the domain with every settings block populated', async () => {
    const { response } = await createFullDomain();
    expect(response.status).toBe(200);
  });

  it('should return every settings block unchanged on GET', async () => {
    const { key } = await createFullDomain();

    const response = await fixture.client.getDomain(key);
    expect(response.status).toBe(200);

    const body = response.body;
    expect(body.key).toEqual(key);
    expect(body.enabled).toBe(false);
    expect(body.master).toBe(true);
    expect(body.alertEnabled).toBe(true);
    expect(body.tags).toEqual(expect.arrayContaining(['alpha', 'beta']));
    expect(body.vhostMode).toBe(true);
    expect(body.vhosts).toEqual([
      expect.objectContaining({
        host: 'auth.example.com',
        path: `/${key}`,
        overrideEntrypoint: true,
      }),
    ]);

    expect(body.oidc.redirectUriStrictMatching).toBe(true);
    expect(body.oidc.postLogoutRedirectUris).toEqual(['https://app.example.com/post-logout']);
    expect(body.oidc.requestUris).toEqual(['https://app.example.com/request']);
    expect(body.oidc.clientRegistrationSettings).toEqual(
      expect.objectContaining({
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
        dynamicClientRegistrationEnabled: true,
        openDynamicClientRegistrationEnabled: true,
        clientTemplateEnabled: true,
        allowedScopesEnabled: true,
      }),
    );
    expect(body.oidc.securityProfileSettings).toEqual(expect.objectContaining({ enablePlainFapi: true }));
    expect(body.oidc.workloadIdentitySettings).toEqual(
      expect.objectContaining({ enabled: true, allowUnsecuredHttpUri: true }),
    );

    expect(body.loginSettings).toEqual(expect.objectContaining({
      registerEnabled: true,
      forgotPasswordEnabled: true,
      rememberMeEnabled: true,
      hideForm: false,
    }));

    expect(body.accountSettings).toEqual(expect.objectContaining({
      loginAttemptsDetectionEnabled: true,
      maxLoginAttempts: 5,
      loginAttemptsResetTime: 600,
      rememberMe: true,
      rememberMeDuration: 86400,
    }));

    expect(body.passwordSettings).toEqual(expect.objectContaining({
      minLength: 12,
      maxLength: 64,
      includeNumbers: true,
      includeSpecialCharacters: true,
      lettersInMixedCase: true,
    }));

    expect(body.corsSettings).toEqual(expect.objectContaining({
      enabled: true,
      allowCredentials: true,
      maxAge: 3600,
    }));
    expect(body.corsSettings.allowedOrigins).toEqual(['https://app.example.com']);
    expect(body.corsSettings.allowedMethods).toEqual(expect.arrayContaining(['GET', 'POST']));
    expect(body.corsSettings.allowedHeaders).toEqual(expect.arrayContaining(['Authorization', 'Content-Type']));

    expect(body.scim).toEqual(expect.objectContaining({ enabled: true }));
    expect(body.selfServiceAccountManagementSettings).toEqual(expect.objectContaining({ enabled: true }));
  });

  it('should preserve untouched fields when one block is changed on update', async () => {
    const { key } = await createFullDomain();
    const before = await fixture.client.getDomain(key);

    // Modify only the description; every other declared block stays the same
    const mutated = { ...fullDef(key), description: 'updated round-trip description' };
    const put = await fixture.client.putDomain(mutated);
    expect(put.status).toBe(200);

    const after = await fixture.client.getDomain(key);
    expect(after.body.description).toEqual('updated round-trip description');
    // every other block must still match what came back before the update
    expect(after.body.tags).toEqual(before.body.tags);
    expect(after.body.vhosts).toEqual(before.body.vhosts);
    expect(after.body.oidc).toEqual(before.body.oidc);
    expect(after.body.loginSettings).toEqual(before.body.loginSettings);
    expect(after.body.accountSettings).toEqual(before.body.accountSettings);
    expect(after.body.passwordSettings).toEqual(before.body.passwordSettings);
    expect(after.body.corsSettings).toEqual(before.body.corsSettings);
    expect(after.body.scim).toEqual(before.body.scim);
    expect(after.body.selfServiceAccountManagementSettings).toEqual(before.body.selfServiceAccountManagementSettings);
    expect(after.body.createdAt).toEqual(before.body.createdAt);
  });

  it('should strictly reset a settings block omitted from the PUT payload', async () => {
    const { key } = await createFullDomain();

    // Omit loginSettings entirely; declarative semantics must wipe it (null)
    const without = { ...fullDef(key) };
    delete (without as any).loginSettings;

    const put = await fixture.client.putDomain(without);
    expect(put.status).toBe(200);
    expect(put.body.loginSettings ?? null).toBeNull();

    const get = await fixture.client.getDomain(key);
    expect(get.body.loginSettings ?? null).toBeNull();
  });

  it('should not surface any internal-id field on PUT, GET or list', async () => {
    const { key, response: put } = await createFullDomain();
    const get = await fixture.client.getDomain(key);
    const list = await fixture.client.listDomains();
    const fromList = list.body.find((d: any) => d.key === key);

    for (const body of [put.body, get.body, fromList]) {
      expect(body.id).toBeUndefined();
      expect(body.referenceId).toBeUndefined();
      expect(body.referenceType).toBeUndefined();
      expect(body.managedBy).toBeUndefined();
    }
  });
});
