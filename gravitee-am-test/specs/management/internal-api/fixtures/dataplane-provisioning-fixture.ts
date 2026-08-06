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
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';

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

export function connectablePayload(id: string): NewDataPlane {
  return process.env.REPOSITORY_TYPE === 'jdbc'
    ? {
        id,
        name: 'E2E connectable data plane',
        type: 'jdbc',
        configuration: {
          jdbc: {
            driver: 'postgresql',
            host: 'postgres',
            port: 5432,
            database: 'postgres',
            username: 'postgres',
            password: 'postgres',
          },
        },
      }
    : {
        id,
        name: 'E2E connectable data plane',
        type: 'mongodb',
        configuration: { mongodb: { dbname: 'gravitee-am', host: 'mongodb', port: 27017 } },
      };
}

export async function provisionConnectableDataPlane(id: string): Promise<DataPlaneSummary> {
  const created = await createDataPlane(connectablePayload(id));
  if (created.status !== 201) {
    throw new Error(`Unable to provision a connectable data plane: status=${created.status} body=${created.raw}`);
  }
  return created.body;
}

export async function deleteProvisionedDataPlanes(): Promise<void> {
  const listed = await listDataPlanes();
  if (listed.status !== 200) {
    console.warn(`⚠️  Could not list data planes for cleanup: status=${listed.status}`);
    return;
  }
  const provisioned = listed.body.filter((dataPlane) => dataPlane.environmentId === ENVIRONMENT_ID);
  for (const dataPlane of provisioned) {
    const deleted = await deleteDataPlane(dataPlane.id);
    if (deleted.status !== 204) {
      console.warn(`⚠️  Could not clean up data plane [${dataPlane.id}]: status=${deleted.status} body=${deleted.raw}`);
    }
  }
}

export async function provisionDataPlane(id: string): Promise<DataPlaneSummary> {
  const created = await createDataPlane(dataPlanePayload(id));
  if (created.status !== 201) {
    throw new Error(`Unable to provision a data plane: status=${created.status} body=${created.raw}`);
  }
  return created.body;
}

export interface BoundDomain {
  domainId: string;
  dataPlaneId: string;
  accessToken: string;
}

export async function createDomainOnDataPlane(dataPlaneId: string): Promise<BoundDomain> {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('dp-e2e-bound', true), 'Bound to a provisioned data plane', dataPlaneId);
  return { domainId: domain.id, dataPlaneId: domain.dataPlaneId, accessToken };
}

export async function releaseBoundDomain(bound: BoundDomain | undefined): Promise<void> {
  if (bound) {
    await safeDeleteDomain(bound.domainId, bound.accessToken);
  }
}

export async function canBindADomainTo(dataPlaneId: string): Promise<boolean> {
  let bound: BoundDomain | undefined;
  try {
    bound = await createDomainOnDataPlane(dataPlaneId);
    return true;
  } catch (e) {
    return false;
  } finally {
    await releaseBoundDomain(bound);
  }
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
