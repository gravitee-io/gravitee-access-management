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
import {
  AutomationDataPlaneFixture,
  READ_FIELDS,
  SECRET_PASSWORD,
  SECRET_USERNAME,
  connectablePayload,
  dataPlanePayload,
  jdbcPayload,
  setupAutomationDataPlaneFixture,
} from './fixtures/automation-dataplane-fixture';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';

setup(200000);

let fixture: AutomationDataPlaneFixture;

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const expectNoCredentials = (response: { text?: string; body: unknown }) => {
  const raw = response.text ?? JSON.stringify(response.body);
  expect(raw).not.toContain(SECRET_PASSWORD);
  expect(raw).not.toContain(SECRET_USERNAME);
  expect(raw).not.toContain('configuration');
};

describe('Automation API data planes - the permission is granted to no built-in role', () => {
  it('should refuse a write from the admin, who is ORGANIZATION_PRIMARY_OWNER', async () => {
    const id = fixture.reserveId('dp-denied');

    const response = await fixture.adminClient.putDataPlane(dataPlanePayload(id));

    // the Automation API answers a denied permission with a bare 403 and no body
    expect(response.status).toBe(403);
  });

  it('should refuse a delete from the admin', async () => {
    const id = fixture.reserveId('dp-admin-del');
    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);

    const response = await fixture.adminClient.deleteDataPlane(id);

    expect(response.status).toBe(403);
    expect((await fixture.client.getDataPlane(id)).status).toBe(200);
  });

  it('should still let the admin read data planes', async () => {
    const id = fixture.reserveId('dp-admin-read');
    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);

    // reads use the pre-existing data_plane permission, which the built-in roles do have
    expect((await fixture.adminClient.listDataPlanes()).status).toBe(200);
    expect((await fixture.adminClient.getDataPlane(id)).status).toBe(200);
  });

  it('should accept a write from a custom role carrying the permission', async () => {
    const id = fixture.reserveId('dp-granted');

    const response = await fixture.client.putDataPlane(dataPlanePayload(id));

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(id);
  });
});

describe('Automation API data planes - create, read and replay', () => {
  it('should return the connection summary and never the settings', async () => {
    const id = fixture.reserveId('dp-summary');

    const created = await fixture.client.putDataPlane(dataPlanePayload(id));

    expect(created.status).toBe(200);
    expect(created.body.database).toEqual('gravitee-am-e2e-dataplane');
    expect(created.body.hosts).toEqual([expect.any(String)]);
    expect(Object.keys(created.body).sort()).toEqual(expect.arrayContaining(['id', 'database', 'hosts']));
    expect(Object.keys(created.body).every((field) => READ_FIELDS.includes(field))).toBe(true);
    expectNoCredentials(created);
  });

  it('should not leak the settings on a read or a list either', async () => {
    const id = fixture.reserveId('dp-noleak');
    await fixture.client.putDataPlane(dataPlanePayload(id));

    expectNoCredentials(await fixture.client.getDataPlane(id));
    expectNoCredentials(await fixture.client.listDataPlanes());
  });

  it('should list the data plane it created', async () => {
    const id = fixture.reserveId('dp-listed');
    await fixture.client.putDataPlane(dataPlanePayload(id));

    const response = await fixture.client.listDataPlanes();

    expect(response.status).toBe(200);
    expect(response.body.map((dataPlane) => dataPlane.id)).toContain(id);
  });

  it('should replay the same definition without changing what it created', async () => {
    const id = fixture.reserveId('dp-replay');
    const first = await fixture.client.putDataPlane(dataPlanePayload(id));
    expect(first.status).toBe(200);

    const second = await fixture.client.putDataPlane(dataPlanePayload(id));

    expect(second.status).toBe(200);
    expect(second.body.createdAt).toEqual(first.body.createdAt);
    expect(new Date(second.body.updatedAt).getTime()).toBeGreaterThanOrEqual(new Date(first.body.updatedAt).getTime());
  });

  it('should return 404 for a data plane that does not exist', async () => {
    const response = await fixture.client.getDataPlane('dp-never-provisioned');

    expect(response.status).toBe(404);
  });
});

describe('Automation API data planes - update', () => {
  it('should apply a new name and gateway url', async () => {
    const id = fixture.reserveId('dp-renamed');
    await fixture.client.putDataPlane(dataPlanePayload(id));

    const updated = await fixture.client.putDataPlane(
      dataPlanePayload(id, { name: 'Renamed data plane', gatewayUrl: 'https://gateway-renamed.example.com' }),
    );

    expect(updated.status).toBe(200);
    const read = await fixture.client.getDataPlane(id);
    expect(read.body.name).toEqual('Renamed data plane');
    expect(read.body.gatewayUrl).toEqual('https://gateway-renamed.example.com');
  });

  it('should refuse a type change', async () => {
    const id = fixture.reserveId('dp-retyped');
    await fixture.client.putDataPlane(dataPlanePayload(id));

    const response = await fixture.client.putDataPlane(jdbcPayload(id));

    expect(response.status).toBe(400);
    expect(response.body.message).toContain("'type' cannot be changed");
  });

  it('should refuse an id reserved by the gravitee.yml', async () => {
    const response = await fixture.client.putDataPlane(dataPlanePayload('default'));

    expect(response.status).toBe(400);
    expect(response.body.message).toContain('reserved');
  });
});

describe('Automation API data planes - a domain keeps the plane it was created on', () => {
  it('should refuse an apply that moves a domain to another data plane', async () => {
    const id = fixture.reserveId('dp-pinned');
    expect((await fixture.client.putDataPlane(connectablePayload(id))).status).toBe(200);

    const domainKey = uniqueName(`dp-pinned-domain-${process.pid}`, true).toLowerCase();
    const domain = { key: domainKey, name: domainKey, path: `/${domainKey}`, dataPlaneId: id };
    expect((await fixture.adminClient.putDomain(domain)).status).toBe(200);

    try {
      const moved = await fixture.adminClient.putDomain({ ...domain, dataPlaneId: 'default' });

      expect(moved.status).toBe(400);
      expect(moved.body.message).toContain('[dataPlaneId] cannot be changed');
      expect((await fixture.adminClient.putDomain(domain)).status).toBe(200);
    } finally {
      await fixture.adminClient.deleteDomain(domainKey);
    }
  });
});

describe('Automation API data planes - delete', () => {
  it('should refuse to delete a data plane a domain still references', async () => {
    const id = fixture.reserveId('dp-bound');
    expect((await fixture.client.putDataPlane(connectablePayload(id))).status).toBe(200);

    // binding only succeeds if the write registered the data plane on the node serving this request
    const domain = await createDomain(fixture.adminToken, uniqueName('dp-bound-domain', true), 'Bound to a data plane', id);
    expect(domain.dataPlaneId).toEqual(id);

    const refused = await fixture.client.deleteDataPlane(id);
    expect(refused.status).toBe(409);

    await safeDeleteDomain(domain.id, fixture.adminToken);

    const accepted = await fixture.client.deleteDataPlane(id);
    expect(accepted.status).toBe(204);
  });

  it('should return 204 when deleting a data plane that does not exist', async () => {
    const response = await fixture.client.deleteDataPlane('dp-never-provisioned');

    expect(response.status).toBe(204);
  });

  it('should make the data plane unreadable once deleted', async () => {
    const id = fixture.reserveId('dp-gone');
    await fixture.client.putDataPlane(dataPlanePayload(id));

    expect((await fixture.client.deleteDataPlane(id)).status).toBe(204);
    expect((await fixture.client.getDataPlane(id)).status).toBe(404);
  });
});
