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
import { createDomain, patchDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { createUser, getAllUsers } from '@management-commands/user-management-commands';
import { getAuditApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';

setup(200000);

let fixture: AutomationDataPlaneFixture;

// AM-7755: the JDBC columns (id 64, name 128) are narrower than the documented 255
const itMongo = process.env.REPOSITORY_TYPE !== 'jdbc' ? it : it.skip;
// the repoint case needs a second, empty database of the same type, which the JDBC stack does not have
const describeMongo = process.env.REPOSITORY_TYPE !== 'jdbc' ? describe : describe.skip;
// a fixed name so runs do not each leave a database behind; user counts are per domain, so sharing it is safe
const EMPTY_DATABASE = 'gravitee-am-e2e-repoint';

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
    expect((await fixture.client.putDomain(domain)).status).toBe(200);

    try {
      const moved = await fixture.client.putDomain({ ...domain, dataPlaneId: 'default' });

      expect(moved.status).toBe(400);
      expect(moved.body.message).toContain('[dataPlaneId] cannot be changed');
      expect((await fixture.client.putDomain(domain)).status).toBe(200);
    } finally {
      await fixture.client.deleteDomain(domainKey);
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

describe('Automation API data planes - audit', () => {
  // the audit builder is covered by DataPlaneDefinitionServiceTest; this shows the reporter lands it
  it('should land a DATA_PLANE_CREATED audit on the organization that the audit API returns', async () => {
    const id = fixture.reserveId('dp-audited');
    const from = Date.now();
    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);

    // the reporter writes asynchronously (bulk flush on JDBC); `from` keeps earlier runs out of the page
    const page = await retryUntil(
      () =>
        getAuditApi(fixture.adminToken).listOrganizationAudits({
          organizationId: process.env.AM_DEF_ORG_ID,
          type: 'DATA_PLANE_CREATED',
          from,
          size: 50,
        }),
      (result) => (result.data ?? []).some((audit) => audit.target?.id === id),
      { timeoutMillis: 15_000, intervalMillis: 500 },
    );
    const audit = page.data.find((entry) => entry.target?.id === id);

    expect(audit).toMatchObject({
      referenceType: 'organization',
      referenceId: process.env.AM_DEF_ORG_ID,
      actor: { alternativeId: process.env.AM_ADMIN_USERNAME },
      target: { id, type: 'DATA_PLANE' },
      outcome: { status: 'success' },
    });
    expectNoCredentials({ body: audit });
  });
});

describe('Automation API data planes - permissions', () => {
  // the ACL-to-status mapping is covered by DataPlanesResourceTest; this shows a real role grants the partial allowance
  it('should let a role holding DATA_PLANE read and list only read, never write', async () => {
    const id = fixture.reserveId('dp-readonly');
    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);
    const { client } = fixture.reader;
    expect((await client.getDataPlane(id)).status).toBe(200);
    expect((await client.listDataPlanes()).body.map((dataPlane) => dataPlane.id)).toContain(id);

    expect((await client.putDataPlane(dataPlanePayload(id, { name: 'not allowed' }))).status).toBe(403);
    expect((await client.putDataPlane(dataPlanePayload(fixture.reserveId('dp-readonly-new')))).status).toBe(403);
    expect((await client.deleteDataPlane(id)).status).toBe(403);
    // nothing changed behind the 403s
    expect((await fixture.client.getDataPlane(id)).body.name).toBe(dataPlanePayload(id).name);
  });
});

describe('Automation API data planes - stored limits', () => {
  // field validation is covered by DataPlaneDefinitionServiceTest; this shows the backend can store what the API accepts
  itMongo('should store an id of 255 characters, the documented maximum', async () => {
    // the padded id is not the one the fixture reserved, so it is deleted here
    const longest = fixture.reserveId('dp-len').padEnd(255, 'x');

    try {
      expect((await fixture.client.putDataPlane(dataPlanePayload(longest))).status).toBe(200);
      expect((await fixture.client.getDataPlane(longest)).status).toBe(200);
    } finally {
      await fixture.client.deleteDataPlane(longest);
    }
  });
});

describeMongo('Automation API data planes - a bound domain follows its data plane store', () => {
  it('should serve a bound domain from the store the data plane currently points at', async () => {
    // team decision on AM-7741: a data plane can be reconfigured at any time, bound or not
    const id = fixture.reserveId('dp-repoint');
    const storeA = connectablePayload(id);
    const storeB = { ...storeA, configuration: { mongodb: { ...storeA.configuration.mongodb, dbname: EMPTY_DATABASE } } };
    expect((await fixture.client.putDataPlane(storeA)).status).toBe(200);
    const domain = await createDomain(fixture.adminToken, uniqueName('dp-repoint-domain', true), 'Moves with its plane', id);
    await patchDomain(domain.id, fixture.adminToken, { enabled: true });
    const userCount = async () => (await getAllUsers(domain.id, fixture.adminToken)).totalCount;

    try {
      await createUser(domain.id, fixture.adminToken, {
        username: uniqueName('dp-repoint-user', true),
        password: 'Rep0intP@ssw0rd',
        email: 'repoint@example.com',
        firstName: 'Bound',
        lastName: 'User',
        preRegistration: false,
      });
      expect(await userCount()).toBe(1);

      // repoint the plane at an empty database: the domain is served from it at once
      expect((await fixture.client.putDataPlane(storeB)).status).toBe(200);
      expect(await userCount()).toBe(0);

      // and back
      expect((await fixture.client.putDataPlane(storeA)).status).toBe(200);
      expect(await userCount()).toBe(1);
    } finally {
      await safeDeleteDomain(domain.id, fixture.adminToken);
    }
  });
});
