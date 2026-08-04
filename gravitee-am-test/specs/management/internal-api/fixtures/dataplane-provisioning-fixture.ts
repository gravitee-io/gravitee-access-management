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

import {
  createDataPlane,
  DataPlaneSummary,
  deleteDataPlane,
  listDataPlanes,
  NewDataPlane,
} from '@management-commands/dataplane-provisioning-commands';

/**
 * An environment can hold at most one data plane, so a case that needs a provisioned one has to own
 * the binding for its duration. Each case releases the binding around itself rather than sharing a
 * row, which keeps the cases independent of each other and of whatever a previous run left behind.
 */

/** Credentials the spec asserts never come back out. */
export const SECRET_PASSWORD = 'sup3r-s3cret-e2e';
export const SECRET_USERNAME = 'am-e2e-user';

export const DATABASE_NAME = 'gravitee-am-e2e-dataplane';

const MONGO_HOST = 'mongodb';
const MONGO_PORT = 27017;
const JDBC_HOST = 'postgres';
const JDBC_PORT = 5432;

/** The credential-free summary each payload below should be reduced to on the way back out. */
export const MONGO_SUMMARY = { database: DATABASE_NAME, hosts: [`${MONGO_HOST}:${MONGO_PORT}`] };
export const JDBC_SUMMARY = { database: DATABASE_NAME, hosts: [`${JDBC_HOST}:${JDBC_PORT}`] };

const ENVIRONMENT_ID = process.env.AM_DEF_ENV_ID;

/**
 * Points at the mongo the stack already runs, so the row stays loadable once AM-7260 starts
 * connecting to provisioned data planes.
 */
export function dataPlanePayload(id: string): NewDataPlane {
  return {
    id,
    name: 'E2E provisioned data plane',
    type: 'mongodb',
    gatewayUrl: process.env.AM_GATEWAY_URL,
    configuration: {
      mongodb: {
        dbname: DATABASE_NAME,
        host: MONGO_HOST,
        port: MONGO_PORT,
        username: SECRET_USERNAME,
        password: SECRET_PASSWORD,
      },
    },
  };
}

/** The same connection expressed as a uri, which carries the credentials inside the uri itself. */
export function uriFormPayload(id: string): NewDataPlane {
  return {
    id,
    name: 'E2E uri form data plane',
    type: 'mongodb',
    configuration: {
      mongodb: {
        uri: `mongodb://${SECRET_USERNAME}:${SECRET_PASSWORD}@${MONGO_HOST}:${MONGO_PORT}/${DATABASE_NAME}`,
      },
    },
  };
}

/** The other data plane type AM ships, so the endpoints are exercised beyond the mongodb handler. */
export function jdbcPayload(id: string): NewDataPlane {
  return {
    id,
    name: 'E2E jdbc data plane',
    type: 'jdbc',
    configuration: {
      jdbc: {
        driver: 'postgresql',
        host: JDBC_HOST,
        port: JDBC_PORT,
        database: DATABASE_NAME,
        username: SECRET_USERNAME,
        password: SECRET_PASSWORD,
      },
    },
  };
}

/**
 * Frees the environment binding, whichever data plane currently holds it. An environment takes one
 * data plane, so a case that leaves its row behind fails the next one with a 409 rather than on its
 * own merits.
 */
export async function releaseEnvironmentBinding(): Promise<void> {
  const listed = await listDataPlanes();
  const bound = listed.body?.find((dataPlane) => dataPlane.environmentId === ENVIRONMENT_ID);
  if (bound) {
    await deleteDataPlane(bound.id);
  }
}

/**
 * Provisions the data plane a case needs and returns its summary. Always creates: the binding is
 * released around every case, so the stored settings are known to be the ones just submitted rather
 * than whatever an earlier run happened to leave.
 */
export async function provisionDataPlane(id: string): Promise<DataPlaneSummary> {
  const created = await createDataPlane(dataPlanePayload(id));
  if (created.status !== 201) {
    throw new Error(`Unable to provision a data plane: status=${created.status} body=${created.raw}`);
  }
  return created.body;
}

/** Every field the summary is allowed to expose. Anything else is a redaction leak. */
export const ALLOWED_SUMMARY_FIELDS = [
  'id',
  'name',
  'type',
  'gatewayUrl',
  'organizationId',
  'environmentId',
  'database',
  'hosts',
  'createdAt',
  'updatedAt',
];
