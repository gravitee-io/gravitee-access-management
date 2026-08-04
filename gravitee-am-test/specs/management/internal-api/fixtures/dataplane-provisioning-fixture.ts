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

export const SECRET_PASSWORD = 'sup3r-s3cret-e2e';
export const SECRET_USERNAME = 'am-e2e-user';

export const DATABASE_NAME = 'gravitee-am-e2e-dataplane';

const MONGO_HOST = 'mongodb';
const MONGO_PORT = 27017;
const JDBC_HOST = 'postgres';
const JDBC_PORT = 5432;

export const MONGO_SUMMARY = { database: DATABASE_NAME, hosts: [`${MONGO_HOST}:${MONGO_PORT}`] };
export const JDBC_SUMMARY = { database: DATABASE_NAME, hosts: [`${JDBC_HOST}:${JDBC_PORT}`] };

const ENVIRONMENT_ID = process.env.AM_DEF_ENV_ID;

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

export async function deleteProvisionedDataPlanes(): Promise<void> {
  const listed = await listDataPlanes();
  const provisioned = listed.body?.filter((dataPlane) => dataPlane.environmentId === ENVIRONMENT_ID) ?? [];
  for (const dataPlane of provisioned) {
    await deleteDataPlane(dataPlane.id);
  }
}

export async function provisionDataPlane(id: string): Promise<DataPlaneSummary> {
  const created = await createDataPlane(dataPlanePayload(id));
  if (created.status !== 201) {
    throw new Error(`Unable to provision a data plane: status=${created.status} body=${created.raw}`);
  }
  return created.body;
}

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
