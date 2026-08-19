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
  forgetDataPlane,
  NewDataPlane,
  provisionedDataPlaneIds,
} from '@management-commands/dataplane-provisioning-commands';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';

export const SECRET_PASSWORD = 'sup3r-s3cret-e2e';
export const SECRET_USERNAME = 'am-e2e-user';

export const DATABASE_NAME = 'gravitee-am-e2e-dataplane';

/**
 * The store as the management API reaches it, which is a docker service name when it runs in the
 * stack and localhost when it is run natively, so it comes from the setup files' AM_INTERNAL_* pair
 * falling back to the runner's view, same as idps-commands.ts and the cloud fixture. Only the checks
 * that expect a store to answer care, but they all read the same value so a definition cannot be half
 * reachable. The payloads take a host and port rather than a uri, hence the parse.
 */
const mongoUri = new URL(process.env.AM_INTERNAL_MONGODB_URI ?? process.env.AM_MONGODB_URI);
const MONGO_HOST = mongoUri.hostname;
const MONGO_PORT = Number(mongoUri.port);
const JDBC_HOST = process.env.AM_INTERNAL_POSTGRES_HOST ?? process.env.AM_POSTGRES_HOST;
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
            host: JDBC_HOST,
            port: JDBC_PORT,
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
        configuration: { mongodb: { dbname: 'gravitee-am', host: MONGO_HOST, port: MONGO_PORT } },
      };
}

/**
 * A data plane whose store cannot be reached. Everything about the definition is valid, so it is
 * provisioned and registered like any other: only the connection is broken, which is what the
 * verification check is there to catch.
 */
export function unreachablePayload(id: string): NewDataPlane {
  return process.env.REPOSITORY_TYPE === 'jdbc'
    ? {
        id,
        name: 'E2E unreachable data plane',
        type: 'jdbc',
        configuration: {
          jdbc: {
            driver: 'postgresql',
            host: JDBC_HOST,
            port: 9999,
            database: 'postgres',
            username: 'postgres',
            password: 'postgres',
          },
        },
      }
    : {
        id,
        name: 'E2E unreachable data plane',
        type: 'mongodb',
        configuration: { mongodb: { dbname: 'gravitee-am', host: MONGO_HOST, port: 9999 } },
      };
}

/**
 * A data plane whose store is reachable but rejects the credentials. The definition is well formed,
 * so provisioning stores it: only the authentication fails.
 */
export function wrongCredentialsPayload(id: string): NewDataPlane {
  return process.env.REPOSITORY_TYPE === 'jdbc'
    ? {
        id,
        name: 'E2E wrong credentials data plane',
        type: 'jdbc',
        configuration: {
          jdbc: {
            driver: 'postgresql',
            host: JDBC_HOST,
            port: JDBC_PORT,
            database: 'postgres',
            username: 'not-a-user',
            password: 'not-a-password',
          },
        },
      }
    : {
        id,
        name: 'E2E wrong credentials data plane',
        type: 'mongodb',
        configuration: {
          mongodb: { dbname: 'gravitee-am', host: MONGO_HOST, port: MONGO_PORT, username: 'not-a-user', password: 'not-a-password' },
        },
      };
}

export async function provisionWrongCredentialsDataPlane(id: string): Promise<DataPlaneSummary> {
  const created = await createDataPlane(wrongCredentialsPayload(id));
  if (created.status !== 201) {
    throw new Error(`Unable to provision a data plane with wrong credentials: status=${created.status} body=${created.raw}`);
  }
  return created.body;
}

export async function provisionUnreachableDataPlane(id: string): Promise<DataPlaneSummary> {
  const created = await createDataPlane(unreachablePayload(id));
  if (created.status !== 201) {
    throw new Error(`Unable to provision an unreachable data plane: status=${created.status} body=${created.raw}`);
  }
  return created.body;
}

/** What the console offers in the new domain form, which reads the registry rather than the stored rows. */
export async function dataPlanesOfferedByTheConsole(): Promise<string[]> {
  const accessToken = await requestAdminAccessToken();
  const response = await fetch(
    `${process.env.AM_MANAGEMENT_URL}/management/organizations/${process.env.AM_DEF_ORG_ID}/environments/${ENVIRONMENT_ID}/data-planes`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  if (response.status !== 200) {
    throw new Error(`Unable to list the data planes the console offers: status=${response.status}`);
  }
  return (await response.json()).map((dataPlane) => dataPlane.id);
}

export async function provisionConnectableDataPlane(id: string): Promise<DataPlaneSummary> {
  const created = await createDataPlane(connectablePayload(id));
  if (created.status !== 201) {
    throw new Error(`Unable to provision a connectable data plane: status=${created.status} body=${created.raw}`);
  }
  return created.body;
}

/**
 * Drops only the data planes this spec file provisioned. Sweeping the whole environment instead would
 * delete the ones a spec on another jest worker is still using, and that spec then fails to bind a
 * domain to a data plane that vanished under it.
 */
export async function deleteProvisionedDataPlanes(): Promise<void> {
  for (const id of provisionedDataPlaneIds()) {
    const deleted = await deleteDataPlane(id);
    // 404 is fine: the test deleted it itself, which is what several of them are about
    if (deleted.status !== 204 && deleted.status !== 404) {
      console.warn(`⚠️  Could not clean up data plane [${id}]: status=${deleted.status} body=${deleted.raw}`);
    }
    forgetDataPlane(id);
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

/**
 * The outcome of binding a domain, with the domain released again either way. The generated sdk
 * resolves with the parsed domain and throws on anything else, so a bind that worked carries no
 * status to assert on: `bound` is what says it worked, and the status belongs to the refusal.
 */
export type BindAttempt = { bound: true; domainId: string } | { bound: false; status: number | undefined; body: string };

export async function attemptBindingADomainTo(dataPlaneId: string): Promise<BindAttempt> {
  let domain: BoundDomain | undefined;
  try {
    domain = await createDomainOnDataPlane(dataPlaneId);
    return { bound: true, domainId: domain.domainId };
  } catch (err: any) {
    // the sdk reads the body into the message itself, so the response cannot be read a second time
    return { bound: false, status: err.response?.status, body: err.message };
  } finally {
    // a domain left bound blocks its data plane from being deleted, which would poison every later run
    await releaseBoundDomain(domain);
  }
}

export async function canBindADomainTo(dataPlaneId: string): Promise<boolean> {
  const attempt = await attemptBindingADomainTo(dataPlaneId);
  if (attempt.bound) {
    return true;
  }
  // only an unknown data plane means "no": anything else is the test failing for another reason
  if (attempt.status !== 400) {
    throw new Error(`Binding a domain to data plane [${dataPlaneId}] failed with status=${attempt.status}: ${attempt.body}`);
  }
  return false;
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
