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

import { afterEach, beforeEach, describe, expect, it } from '@jest/globals';
import {
  createDataPlane,
  DataPlaneSummary,
  deleteDataPlane,
  getDataPlane,
  listDataPlanes,
  onPublicManagementPort,
  unauthenticated,
  wrongCredentials,
} from '@management-commands/dataplane-provisioning-commands';
import {
  ALLOWED_SUMMARY_FIELDS,
  DATABASE_NAME,
  dataPlanePayload,
  JDBC_SUMMARY,
  jdbcPayload,
  MONGO_SUMMARY,
  provisionDataPlane,
  releaseEnvironmentBinding,
  SECRET_PASSWORD,
  SECRET_USERNAME,
  uriFormPayload,
} from './fixtures/dataplane-provisioning-fixture';
import { setup } from '../../test-fixture';

setup(60000);

const PROVISIONED_ID = 'dp-e2e-provisioned';

/** Fails the assertion if the serialised payload carries anything credential shaped. */
function expectNoCredentials(raw: string) {
  expect(raw).not.toContain(SECRET_PASSWORD);
  expect(raw).not.toContain(SECRET_USERNAME);
  expect(raw).not.toContain('password');
  expect(raw).not.toContain('configuration');
}

describe('Data plane provisioning (management technical API)', () => {
  // an environment holds at most one data plane, so every case starts and ends with it unbound
  beforeEach(releaseEnvironmentBinding);
  afterEach(releaseEnvironmentBinding);

  describe('provisioning', () => {
    let provisioned: DataPlaneSummary;

    beforeEach(async () => {
      provisioned = await provisionDataPlane(PROVISIONED_ID);
    });

    it('exposes the connection metadata without the credentials', async () => {
      const response = await getDataPlane(provisioned.id);

      expect(response.status).toBe(200);
      expect(response.body).toMatchObject({
        id: provisioned.id,
        type: 'mongodb',
        environmentId: process.env.AM_DEF_ENV_ID,
        organizationId: process.env.AM_DEF_ORG_ID,
      });
      expectNoCredentials(response.raw);
    });

    it('summarises the submitted connection settings', async () => {
      const response = await getDataPlane(provisioned.id);

      expect(response.body).toMatchObject(MONGO_SUMMARY);
    });

    it('returns only the allowed summary fields', async () => {
      const response = await getDataPlane(provisioned.id);

      expect(Object.keys(response.body).sort()).toEqual(ALLOWED_SUMMARY_FIELDS.filter((field) => field in response.body).sort());
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
      const [list, get, create, del] = await Promise.all([
        unauthenticated.list(),
        unauthenticated.get(PROVISIONED_ID),
        unauthenticated.create(dataPlanePayload('dp-e2e-unauthenticated')),
        unauthenticated.delete(PROVISIONED_ID),
      ]);

      expect(list.status).toBe(401);
      expect(get.status).toBe(401);
      expect(create.status).toBe(401);
      expect(del.status).toBe(401);
    });

    it('rejects credentials that are not the ones the technical api was configured with', async () => {
      const [list, create] = await Promise.all([
        wrongCredentials.list(),
        wrongCredentials.create(dataPlanePayload('dp-e2e-wrong-credentials')),
      ]);

      expect(list.status).toBe(401);
      expect(create.status).toBe(401);
    });

    it('does not serve the data plane routes on the console facing port', async () => {
      const [list, get] = await Promise.all([
        onPublicManagementPort('/_node/dataplanes'),
        onPublicManagementPort(`/_node/dataplanes/${PROVISIONED_ID}`),
      ]);

      expect(list.status).toBe(404);
      expect(get.status).toBe(404);
    });
  });

  describe('deletion', () => {
    it('returns 404 for an unknown data plane', async () => {
      const response = await deleteDataPlane('dp-e2e-does-not-exist');

      expect(response.status).toBe(404);
      expect(response.raw).toContain('can not be found');
    });

    it('deletes the provisioned data plane and frees its environment', async () => {
      const provisioned = await provisionDataPlane(PROVISIONED_ID);

      const deleted = await deleteDataPlane(provisioned.id);
      expect(deleted.status).toBe(204);

      const after = await getDataPlane(provisioned.id);
      expect(after.status).toBe(404);

      // the environment binding is released, so provisioning works again
      const recreated = await createDataPlane(dataPlanePayload(PROVISIONED_ID));
      expect(recreated.status).toBe(201);
    });
  });

  describe('configuration forms', () => {
    it('summarises a connection uri without its userinfo', async () => {
      const id = 'dp-e2e-uri-form';

      const created = await createDataPlane(uriFormPayload(id));
      expect(created.status).toBe(201);
      expect(created.body).toMatchObject(MONGO_SUMMARY);
      expectNoCredentials(created.raw);

      // the credentials live in the uri itself, so the read path has to strip them out again
      const read = await getDataPlane(id);
      expect(read.body).toMatchObject(MONGO_SUMMARY);
      expectNoCredentials(read.raw);
    });

    it('provisions a jdbc data plane', async () => {
      const created = await createDataPlane(jdbcPayload('dp-e2e-jdbc-form'));

      expect(created.status).toBe(201);
      expect(created.body).toMatchObject({ type: 'jdbc', ...JDBC_SUMMARY });
      expectNoCredentials(created.raw);
    });
  });
});
