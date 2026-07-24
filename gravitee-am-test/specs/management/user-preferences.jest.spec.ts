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
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { performGet, performPut } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import type { Domain } from '@management-models/Domain';
import { setup } from '../test-fixture';

setup();

const managementUrl = () => `${process.env.AM_MANAGEMENT_URL}/management`;

let accessToken: string;
let domainA: Domain;
let domainB: Domain;

const authHeaders = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${accessToken}`,
});

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
  domainA = await createDomain(accessToken, uniqueName('prefs-a', true), 'user preferences spec domain A');
  domainB = await createDomain(accessToken, uniqueName('prefs-b', true), 'user preferences spec domain B');
});

afterAll(async () => {
  // reset the shared admin user's preferences and remove the spec's domains
  await performPut(managementUrl(), '/user/preferences', {}, authHeaders());
  await safeDeleteDomain(domainA?.id, accessToken);
  await safeDeleteDomain(domainB?.id, accessToken);
});

describe('Console user preferences', () => {
  it('should update and fetch preferences', async () => {
    const preferences = {
      defaultDomainId: domainA.id,
      defaultEnvironmentId: process.env.AM_DEF_ENV_ID,
      pinnedDomainIds: [domainA.id, domainB.id],
    };

    const putResponse = await performPut(managementUrl(), '/user/preferences', preferences, authHeaders());
    expect(putResponse.status).toBe(200);
    expect(putResponse.body).toMatchObject(preferences);

    const getResponse = await performGet(managementUrl(), '/user/preferences', authHeaders());
    expect(getResponse.status).toBe(200);
    expect(getResponse.body).toMatchObject(preferences);
  });

  it('should replace the whole preferences object on PUT, not merge fields', async () => {
    const initial = {
      defaultDomainId: domainA.id,
      defaultEnvironmentId: process.env.AM_DEF_ENV_ID,
      pinnedDomainIds: [domainA.id, domainB.id],
    };
    const initialResponse = await performPut(managementUrl(), '/user/preferences', initial, authHeaders());
    expect(initialResponse.status).toBe(200);

    const partialUpdate = { pinnedDomainIds: [domainB.id] };
    const putResponse = await performPut(managementUrl(), '/user/preferences', partialUpdate, authHeaders());
    expect(putResponse.status).toBe(200);
    expect(putResponse.body.pinnedDomainIds).toEqual([domainB.id]);
    expect(putResponse.body.defaultDomainId).toBeUndefined();
    expect(putResponse.body.defaultEnvironmentId).toBeUndefined();

    const getResponse = await performGet(managementUrl(), '/user/preferences', authHeaders());
    expect(getResponse.status).toBe(200);
    expect(getResponse.body.pinnedDomainIds).toEqual([domainB.id]);
    expect(getResponse.body.defaultDomainId).toBeUndefined();
    expect(getResponse.body.defaultEnvironmentId).toBeUndefined();
  });

  it('should accept exactly 50 pinned domains', async () => {
    const preferences = { pinnedDomainIds: Array.from({ length: 50 }, (_, i) => `domain-${i}`) };

    const response = await performPut(managementUrl(), '/user/preferences', preferences, authHeaders());

    expect(response.status).toBe(200);
    expect(response.body.pinnedDomainIds).toHaveLength(50);
  });

  it('should reject more than 50 pinned domains', async () => {
    const preferences = { pinnedDomainIds: Array.from({ length: 51 }, (_, i) => `domain-${i}`) };

    const response = await performPut(managementUrl(), '/user/preferences', preferences, authHeaders());

    expect(response.status).toBe(400);
  });

  it('should reject unauthenticated request', async () => {
    const response = await performGet(managementUrl(), '/user/preferences', { 'Content-Type': 'application/json' });

    expect(response.status).toBe(401);
  });

  it('should record an audit entry when preferences are updated', async () => {
    const putResponse = await performPut(
      managementUrl(),
      '/user/preferences',
      { defaultDomainId: domainA.id, defaultEnvironmentId: process.env.AM_DEF_ENV_ID },
      authHeaders(),
    );
    expect(putResponse.status).toBe(200);

    const auditResponse = await performGet(getOrganisationManagementUrl(), '/audits?type=USER_PREFERENCES_UPDATED&size=1', authHeaders());

    expect(auditResponse.status).toBe(200);
    expect(auditResponse.body.data.length).toBeGreaterThan(0);
    expect(auditResponse.body.data[0]).toMatchObject({
      type: 'USER_PREFERENCES_UPDATED',
      outcome: { status: 'success' },
    });
  });
});

describe('Domains list filtered by ids', () => {
  it('should return only the requested domains', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      `/environments/${process.env.AM_DEF_ENV_ID}/domains?ids=${domainA.id}`,
      authHeaders(),
    );

    expect(response.status).toBe(200);
    expect(response.body.data.map((domain) => domain.id)).toEqual([domainA.id]);
    expect(response.body.totalCount).toBe(1);
  });

  it('should reject more than 50 ids', async () => {
    const ids = Array.from({ length: 51 }, (_, i) => `domain-${i}`).join('&ids=');

    const response = await performGet(
      getOrganisationManagementUrl(),
      `/environments/${process.env.AM_DEF_ENV_ID}/domains?ids=${ids}`,
      authHeaders(),
    );

    expect(response.status).toBe(400);
  });
});
