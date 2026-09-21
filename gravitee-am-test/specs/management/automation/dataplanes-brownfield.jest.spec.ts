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
import { AutomationDataPlaneFixture, connectablePayload, setupAutomationDataPlaneFixture } from './fixtures/automation-dataplane-fixture';
import {
  createDataPlane as provisionOutsideAutomation,
  deleteDataPlane as deprovisionOutsideAutomation,
  getDataPlane as readOutsideAutomation,
} from '@management-commands/dataplane-provisioning-commands';
import { uniqueName } from '@utils-commands/misc';

setup(200000);

/**
 * The id: resolver is covered by DataPlanesResourceTest; what only a running stack can show is that
 * the internal API and the Automation API read and write the same store, and that an id: update
 * leaves ownership (managedBy) untouched.
 */

let fixture: AutomationDataPlaneFixture;
let brownfieldId: string;

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  if (brownfieldId) {
    await deprovisionOutsideAutomation(brownfieldId).catch(() => undefined);
  }
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API data planes - brownfield', () => {
  it('should share one store with the internal API and keep a brownfield data plane unmanaged through an id: update', async () => {
    brownfieldId = uniqueName(`bf-shared-${process.pid}`, true).toLowerCase();
    const created = await provisionOutsideAutomation(connectablePayload(brownfieldId));
    expect(created.status).toBe(201);
    expect(created.body.managedBy).toBe('NONE');

    // visible to the Automation API only through id:, never in its list or by bare key
    expect((await fixture.client.getDataPlane(brownfieldId)).status).toBe(404);
    expect((await fixture.client.listDataPlanes()).body.map((dataPlane) => dataPlane.id)).not.toContain(brownfieldId);
    expect((await fixture.client.getDataPlane(`id:${brownfieldId}`)).status).toBe(200);

    const updated = await fixture.client.putDataPlane({
      ...connectablePayload(brownfieldId),
      id: `id:${brownfieldId}`,
      name: 'Updated through id:',
      gatewayUrl: 'https://gateway-brownfield.example.com',
    });
    expect(updated.status).toBe(200);
    expect(updated.body).toMatchObject({
      id: brownfieldId,
      name: 'Updated through id:',
      gatewayUrl: 'https://gateway-brownfield.example.com',
    });

    // the internal API reads the change back from the same row, and ownership has not moved
    const outside = await readOutsideAutomation(brownfieldId);
    expect(outside.status).toBe(200);
    expect(outside.body).toMatchObject({
      name: 'Updated through id:',
      gatewayUrl: 'https://gateway-brownfield.example.com',
      managedBy: 'NONE',
    });
    expect((await fixture.client.listDataPlanes()).body.map((dataPlane) => dataPlane.id)).not.toContain(brownfieldId);
  });
});
