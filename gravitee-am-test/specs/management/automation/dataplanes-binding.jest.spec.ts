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
import { createDomain, patchDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { createUser, getAllUsers } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';

setup(200000);

/**
 * Only a running stack can show a bound domain following its data plane to another store.
 * Needs a second, empty database of the same type, which the JDBC stack does not have.
 */

let fixture: AutomationDataPlaneFixture;

const describeMongo = process.env.REPOSITORY_TYPE !== 'jdbc' ? describe : describe.skip;
/** A fixed name, so runs do not leave a fresh database behind each; user counts are per domain, so sharing it is safe. */
const EMPTY_DATABASE = 'gravitee-am-e2e-repoint';

const userCount = async (domainId: string): Promise<number> => (await getAllUsers(domainId, fixture.adminToken)).totalCount;

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describeMongo('Automation API data planes - a bound domain follows its data plane store', () => {
  it('should serve a bound domain from the store the data plane currently points at', async () => {
    // team decision on AM-7741: a data plane can be reconfigured at any time, bound or not
    const id = fixture.reserveId('bind-repoint');
    const storeA = connectablePayload(id);
    const storeB = { ...storeA, configuration: { mongodb: { ...storeA.configuration.mongodb, dbname: EMPTY_DATABASE } } };
    expect((await fixture.client.putDataPlane(storeA)).status).toBe(200);
    const domain = await createDomain(fixture.adminToken, uniqueName('bind-repoint-domain', true), 'Moves with its plane', id);
    await patchDomain(domain.id, fixture.adminToken, { enabled: true });

    try {
      await createUser(domain.id, fixture.adminToken, {
        username: uniqueName('bind-user', true),
        password: 'Bind1ngP@ssw0rd',
        email: 'bind@example.com',
        firstName: 'Bound',
        lastName: 'User',
        preRegistration: false,
      });
      expect(await userCount(domain.id)).toBe(1);

      // repoint the plane at an empty database: the domain is served from it at once
      expect((await fixture.client.putDataPlane(storeB)).status).toBe(200);
      expect(await userCount(domain.id)).toBe(0);

      // and back
      expect((await fixture.client.putDataPlane(storeA)).status).toBe(200);
      expect(await userCount(domain.id)).toBe(1);
    } finally {
      await safeDeleteDomain(domain.id, fixture.adminToken);
    }
  });
});
