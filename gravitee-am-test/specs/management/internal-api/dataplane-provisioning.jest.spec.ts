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
import {
  createDataPlane,
  DataPlaneSummary,
  getDataPlane,
  listDataPlanes,
  unauthenticated,
} from '@management-commands/dataplane-provisioning-commands';
import {
  ALLOWED_SUMMARY_FIELDS,
  DATABASE_NAME,
  dataPlanePayload,
  ensureProvisionedDataPlane,
  SECRET_PASSWORD,
  SECRET_USERNAME,
} from './fixtures/dataplane-provisioning-fixture';

global.fetch = fetch;
jest.setTimeout(60000);

const PROVISIONED_ID = 'dp-e2e-provisioned';

let provisioned: DataPlaneSummary;
/** False when the row was left behind by an earlier run, so its stored values are not the ones we submitted. */
let created: boolean;

/** Fails the assertion if the serialised payload carries anything credential shaped. */
function expectNoCredentials(raw: string) {
  expect(raw).not.toContain(SECRET_PASSWORD);
  expect(raw).not.toContain(SECRET_USERNAME);
  expect(raw).not.toContain('password');
  expect(raw).not.toContain('configuration');
}

describe('Data plane provisioning (management technical API)', () => {
  beforeAll(async () => {
    ({ summary: provisioned, created } = await ensureProvisionedDataPlane(PROVISIONED_ID));
  });

  describe('provisioning', () => {
    it('exposes the connection metadata without the credentials', async () => {
      const response = await getDataPlane(provisioned.id);

      expect(response.status).toBe(200);
      expect(response.body).toMatchObject({
        id: provisioned.id,
        type: 'mongodb',
        environmentId: process.env.AM_DEF_ENV_ID,
        organizationId: process.env.AM_DEF_ORG_ID,
      });
      expect(response.body.database).toEqual(expect.any(String));
      expect(response.body.hosts).toEqual([expect.stringMatching(/^[^@]+:\d+$/)]);
      expectNoCredentials(response.raw);
    });

    it('summarises the submitted connection settings', async () => {
      if (!created) {
        // an earlier run bound the environment, so the stored settings are not the ones above
        return;
      }
      const response = await getDataPlane(provisioned.id);

      expect(response.body).toMatchObject({ database: DATABASE_NAME, hosts: ['mongodb:27017'] });
    });

    it('returns only the allowed summary fields', async () => {
      const response = await getDataPlane(provisioned.id);

      expect(Object.keys(response.body).sort()).toEqual(
        ALLOWED_SUMMARY_FIELDS.filter((field) => field in response.body).sort(),
      );
    });

    it('lists the provisioned data plane, still redacted', async () => {
      const response = await listDataPlanes();

      expect(response.status).toBe(200);
      expect(response.body.map((dataPlane) => dataPlane.id)).toContain(provisioned.id);
      expectNoCredentials(response.raw);
    });

    it('rejects a second data plane for an environment that is already bound', async () => {
      const response = await createDataPlane(dataPlanePayload('dp-e2e-second-on-same-env'));

      expect(response.status).toBe(409);
      expect(response.body.message).toContain(`Environment [${process.env.AM_DEF_ENV_ID}] is already bound`);
    });

    it('rejects an id that is already taken', async () => {
      const response = await createDataPlane(dataPlanePayload(provisioned.id));

      expect(response.status).toBe(409);
      expect(response.body.message).toContain(`[${provisioned.id}] already exists`);
    });
  });

  describe('reads', () => {
    it('returns 404 for an unknown data plane', async () => {
      const response = await getDataPlane('dp-e2e-does-not-exist');

      expect(response.status).toBe(404);
      expect(response.raw).toContain('can not be found');
    });
  });

  describe('validation', () => {
    it('rejects a mongodb configuration without a database, which would silently use the shared one', async () => {
      const response = await createDataPlane({
        id: 'dp-e2e-no-dbname',
        name: 'No dbname',
        type: 'mongodb',
        configuration: { mongodb: { host: 'mongodb' } },
      });

      expect(response.status).toBe(400);
      expect(response.body.message).toContain("requires either 'uri' or 'dbname'");
    });

    it('rejects a mongodb configuration without a host, which would silently use localhost', async () => {
      const response = await createDataPlane({
        id: 'dp-e2e-no-host',
        name: 'No host',
        type: 'mongodb',
        configuration: { mongodb: { dbname: DATABASE_NAME } },
      });

      expect(response.status).toBe(400);
      expect(response.body.message).toContain("requires 'host'");
    });

    it('rejects the reserved default id', async () => {
      const response = await createDataPlane(dataPlanePayload('default'));

      expect(response.status).toBe(400);
      expect(response.body.message).toContain('is reserved');
    });

    it('rejects a type with no deployed plugin', async () => {
      const response = await createDataPlane({
        id: 'dp-e2e-unknown-type',
        name: 'Unknown type',
        type: 'cassandra',
        configuration: { mongodb: { dbname: DATABASE_NAME, host: 'mongodb' } },
      });

      expect(response.status).toBe(400);
      expect(response.body.message).toContain('No data plane plugin is deployed for type [cassandra]');
    });

    it('rejects a configuration that declares a foreign block', async () => {
      const response = await createDataPlane({
        id: 'dp-e2e-foreign-block',
        name: 'Foreign block',
        type: 'mongodb',
        configuration: { mongodb: { dbname: DATABASE_NAME, host: 'mongodb' }, jdbc: { host: 'postgres' } },
      });

      expect(response.status).toBe(400);
      expect(response.body.message).toContain('found also: jdbc');
    });

    it('names an unrecognised field rather than silently dropping it', async () => {
      const response = await createDataPlane({
        ...dataPlanePayload('dp-e2e-typo'),
        gatewayURL: 'https://typo',
      } as never);

      expect(response.status).toBe(400);
      expect(response.body.message).toContain('unknown field [gatewayURL]');
    });

    it('rejects malformed json without echoing the body back', async () => {
      const response = await createDataPlane(`{"id":"dp-e2e-broken","password":"${SECRET_PASSWORD}",,}`);

      expect(response.status).toBe(400);
      expect(response.raw).not.toContain(SECRET_PASSWORD);
    });
  });

  describe('authentication', () => {
    it('rejects unauthenticated reads and writes', async () => {
      const [list, get, create] = await Promise.all([
        unauthenticated.list(),
        unauthenticated.get(PROVISIONED_ID),
        unauthenticated.create(dataPlanePayload('dp-e2e-unauthenticated')),
      ]);

      expect(list.status).toBe(401);
      expect(get.status).toBe(401);
      expect(create.status).toBe(401);
    });
  });

  afterAll(async () => {
    // AM-7259 has no delete endpoint, so the provisioned row intentionally outlives the run and is
    // adopted by the next one.
  });
});
