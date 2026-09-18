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
  connectablePayload,
  dataPlanePayload,
  jdbcPayload,
  setupAutomationDataPlaneFixture,
} from './fixtures/automation-dataplane-fixture';
import {
  createDataPlane as provisionOutsideAutomation,
  deleteDataPlane as deprovisionOutsideAutomation,
  getDataPlane as readOutsideAutomation,
} from '@management-commands/dataplane-provisioning-commands';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';

setup(200000);

/**
 * Data planes provisioned outside the Automation API (here: through the node's internal API) are
 * "brownfield": stored with managedBy NONE, invisible to the automation list, and reachable only
 * through the `id:` prefix, which updates them in place without adopting them.
 */

let fixture: AutomationDataPlaneFixture;
/** Brownfield ids this spec provisioned through the internal API, removed the same way. */
const brownfield: string[] = [];

const brownfieldPlane = async (prefix: string) => {
  const id = uniqueName(`${prefix}-${process.pid}`, true).toLowerCase();
  const created = await provisionOutsideAutomation(connectablePayload(id));
  expect(created.status).toBe(201);
  expect(created.body.managedBy).toBe('NONE');
  brownfield.push(id);
  return id;
};

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  for (const id of brownfield) {
    await deprovisionOutsideAutomation(id).catch(() => undefined);
  }
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API data planes - brownfield visibility', () => {
  it('should keep a data plane provisioned outside the Automation API out of the list', async () => {
    const id = await brownfieldPlane('bf-hidden');
    // a managed sibling proves the list is filtering, not empty
    const managed = fixture.reserveId('bf-visible');
    expect((await fixture.client.putDataPlane(connectablePayload(managed))).status).toBe(200);

    const listed = await fixture.client.listDataPlanes();

    expect(listed.status).toBe(200);
    const ids = listed.body.map((dataPlane) => dataPlane.id);
    expect(ids).toContain(managed);
    expect(ids).not.toContain(id);
  });

  it('should not resolve a brownfield data plane by bare key', async () => {
    const id = await brownfieldPlane('bf-bare');

    const response = await fixture.client.getDataPlane(id);

    expect(response.status).toBe(404);
    expect(response.body.message).toBe(`Data plane [${id}] can not be found`);
  });

  it('should resolve a brownfield data plane through the id: prefix, without its settings', async () => {
    const id = await brownfieldPlane('bf-id');

    const response = await fixture.client.getDataPlane(`id:${id}`);

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ id, type: connectablePayload(id).type });
    expect(response.body).not.toHaveProperty('configuration');
  });

  it('should also reach an Automation-managed data plane through the id: prefix', async () => {
    const id = fixture.reserveId('bf-managed');
    expect((await fixture.client.putDataPlane(connectablePayload(id))).status).toBe(200);

    const response = await fixture.client.getDataPlane(`id:${id}`);

    expect(response.status).toBe(200);
    expect(response.body.id).toBe(id);
  });
});

describe('Automation API data planes - brownfield writes', () => {
  it('should refuse a bare-key PUT on an id taken by a brownfield data plane', async () => {
    const id = await brownfieldPlane('bf-taken');

    const response = await fixture.client.putDataPlane(connectablePayload(id));

    expect(response.status).toBe(409);
    expect(response.body.message).toBe(`A data plane with id [${id}] already exists`);
  });

  it('should update a brownfield data plane in place through id: without adopting it', async () => {
    const id = await brownfieldPlane('bf-update');
    const updated = {
      ...connectablePayload(id),
      id: `id:${id}`,
      name: 'Updated through id:',
      gatewayUrl: 'https://gateway-brownfield.example.com',
    };

    const response = await fixture.client.putDataPlane(updated);

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ id, name: updated.name, gatewayUrl: updated.gatewayUrl });

    // the internal API sees the change, and ownership has not moved
    const outside = await readOutsideAutomation(id);
    expect(outside.body).toMatchObject({ name: updated.name, gatewayUrl: updated.gatewayUrl, managedBy: 'NONE' });
    const listed = await fixture.client.listDataPlanes();
    expect(listed.body.map((dataPlane) => dataPlane.id)).not.toContain(id);
  });

  it('should refuse a type change through id:', async () => {
    const id = await brownfieldPlane('bf-retype');
    // a well-formed definition of the other type, so only the type check can refuse it
    const otherType = connectablePayload(id).type === 'mongodb' ? jdbcPayload(id) : dataPlanePayload(id);

    const response = await fixture.client.putDataPlane({ ...otherType, id: `id:${id}` });

    expect(response.status).toBe(400);
    expect(response.body.message).toBe(`Once a data plane is created, 'type' cannot be changed [${id}]`);
  });

  it('should treat the data plane declared in gravitee.yml as outside the API', async () => {
    expect((await fixture.client.getDataPlane('id:default')).status).toBe(404);
    expect((await fixture.client.putDataPlane({ ...connectablePayload('x'), id: 'id:default' })).status).toBe(404);
    expect((await fixture.client.deleteDataPlane('id:default')).status).toBe(204);

    // still there for domains
    const domain = await createDomain(fixture.adminToken, uniqueName('bf-default-domain', true), 'On the yml plane', 'default');
    expect(domain.dataPlaneId).toBe('default');
    await safeDeleteDomain(domain.id, fixture.adminToken);
  });
});

describe('Automation API data planes - brownfield domains and deletion', () => {
  it('should bind domains to a brownfield data plane by its raw id, never by id:', async () => {
    const id = await brownfieldPlane('bf-bind');

    const domain = await createDomain(fixture.adminToken, uniqueName('bf-bound-domain', true), 'Bound to a brownfield plane', id);
    expect(domain.dataPlaneId).toBe(id);

    try {
      const domainKey = uniqueName(`bf-aapi-domain-${process.pid}`, true).toLowerCase();
      const viaAutomation = await fixture.client.putDomain({ key: domainKey, name: domainKey, path: `/${domainKey}`, dataPlaneId: id });
      expect(viaAutomation.status).toBe(200);
      expect(viaAutomation.body.dataPlaneId).toBe(id);
      await fixture.client.deleteDomain(domainKey);

      const prefixed = await fixture.client.putDomain({
        key: `${domainKey}-x`,
        name: domainKey,
        path: `/${domainKey}-x`,
        dataPlaneId: `id:${id}`,
      });
      expect(prefixed.status).toBe(400);
      expect(prefixed.body.message).toContain(`Data Plane [id:${id}] is not loaded on this node`);
    } finally {
      await safeDeleteDomain(domain.id, fixture.adminToken);
    }
  });

  it('should refuse to delete a referenced brownfield data plane through id:, then allow it', async () => {
    const id = await brownfieldPlane('bf-delete');
    const domain = await createDomain(fixture.adminToken, uniqueName('bf-delete-domain', true), 'Holds the plane', id);

    const refused = await fixture.client.deleteDataPlane(`id:${id}`);
    expect(refused.status).toBe(409);
    expect(refused.body.message).toBe(`Data plane [${id}] is used by at least one domain`);

    await safeDeleteDomain(domain.id, fixture.adminToken);

    expect((await fixture.client.deleteDataPlane(`id:${id}`)).status).toBe(204);
    expect((await fixture.client.getDataPlane(`id:${id}`)).status).toBe(404);
    expect((await readOutsideAutomation(id)).status).toBe(404);
  });
});
