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
import { setup } from '../../test-fixture';
import { dataPlanePayload } from './fixtures/automation-dataplane-fixture';
import {
  AutomationDataPlanePermissionsFixture,
  setupAutomationDataPlanePermissionsFixture,
} from './fixtures/automation-dataplane-permissions-fixture';
import { automationUrl, envPath, jsonHeaders } from './fixtures/automation-client';
import { getAuditApi } from '@management-commands/service/utils';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';

setup(200000);

/**
 * The data plane routes are guarded by the DATA_PLANE permission: READ/LIST for reads, CREATE for a
 * PUT that would create, UPDATE for a PUT that finds a plane, DELETE for deletes. Owner roles hold
 * all five by default; user roles hold READ and LIST only.
 */

let fixture: AutomationDataPlanePermissionsFixture;

beforeAll(async () => {
  fixture = await setupAutomationDataPlanePermissionsFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API data planes - permissions', () => {
  it('should refuse unauthenticated and unrecognised bearer tokens', async () => {
    expect((await performGet(automationUrl(), `${envPath()}/dataplanes`, jsonHeaders())).status).toBe(401);
    expect((await performGet(automationUrl(), `${envPath()}/dataplanes`, jsonHeaders({ Authorization: 'Bearer nope' }))).status).toBe(401);
  });

  it('should let the default ORGANIZATION_USER role read and list but not write', async () => {
    const id = fixture.admin.reserveId('perm-plain');
    expect((await fixture.admin.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);
    const { client } = fixture.plainUser;

    expect((await client.getDataPlane(id)).status).toBe(200);
    expect((await client.listDataPlanes()).status).toBe(200);

    expect((await client.putDataPlane(dataPlanePayload(id, { name: 'not allowed' }))).status).toBe(403);
    expect((await client.putDataPlane(dataPlanePayload(fixture.admin.reserveId('perm-plain-new')))).status).toBe(403);
    expect((await client.deleteDataPlane(id)).status).toBe(403);
    // nothing changed behind the 403s
    expect((await fixture.admin.client.getDataPlane(id)).body.name).toBe(dataPlanePayload(id).name);
  });

  it('should gate writes before revealing whether the data plane exists', async () => {
    const { client } = fixture.reader;
    const ghost = 'perm-never-provisioned';

    expect((await client.putDataPlane(dataPlanePayload(ghost))).status).toBe(403);
    expect((await client.deleteDataPlane(ghost)).status).toBe(403);
    // a read reveals it normally
    expect((await client.getDataPlane(ghost)).status).toBe(404);
  });

  it('should let a custom role holding every DATA_PLANE ACL manage data planes and be the audit actor', async () => {
    const id = fixture.admin.reserveId('perm-manager');
    const { client, username } = fixture.manager;
    const from = Date.now();

    const created = await client.putDataPlane(dataPlanePayload(id));
    expect(created.status).toBe(200);
    expect((await client.putDataPlane(dataPlanePayload(id, { name: 'Managed by a custom role' }))).status).toBe(200);
    expect((await client.getDataPlane(id)).body.name).toBe('Managed by a custom role');
    expect((await client.listDataPlanes()).body.map((dataPlane) => dataPlane.id)).toContain(id);
    expect((await client.deleteDataPlane(id)).status).toBe(204);

    const page = await retryUntil(
      () =>
        getAuditApi(fixture.admin.adminToken).listOrganizationAudits({
          organizationId: process.env.AM_DEF_ORG_ID,
          type: 'DATA_PLANE_CREATED',
          from,
          size: 50,
        }),
      (result) => (result.data ?? []).some((audit) => audit.target?.id === id),
      { timeoutMillis: 30_000, intervalMillis: 500 },
    );
    expect(page.data.find((audit) => audit.target?.id === id).actor).toMatchObject({ alternativeId: username });
  });

  it('should not grant domain permissions along with data plane permissions', async () => {
    const id = fixture.admin.reserveId('perm-no-domain');
    expect((await fixture.manager.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);

    const domain = await fixture.manager.client.putDomain({
      key: `${id}-domain`,
      name: `${id}-domain`,
      path: `/${id}-domain`,
      dataPlaneId: id,
    });

    expect(domain.status).toBe(403);
  });
});
