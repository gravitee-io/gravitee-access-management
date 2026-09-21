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

setup(200000);

/**
 * The ACL-to-status mapping is covered by DataPlanesResourceTest; what only a running stack can show
 * is that a custom organization role granted through a membership yields the partial allowance.
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
  it('should let a role holding DATA_PLANE read and list only read, never write', async () => {
    const id = fixture.admin.reserveId('perm-reader');
    expect((await fixture.admin.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);
    const { client } = fixture.reader;

    expect((await client.getDataPlane(id)).status).toBe(200);
    expect((await client.listDataPlanes()).body.map((dataPlane) => dataPlane.id)).toContain(id);

    expect((await client.putDataPlane(dataPlanePayload(id, { name: 'not allowed' }))).status).toBe(403);
    expect((await client.putDataPlane(dataPlanePayload(fixture.admin.reserveId('perm-reader-new')))).status).toBe(403);
    expect((await client.deleteDataPlane(id)).status).toBe(403);
    // nothing changed behind the 403s
    expect((await fixture.admin.client.getDataPlane(id)).body.name).toBe(dataPlanePayload(id).name);
  });
});
