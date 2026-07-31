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

import { createDataPlane, DataPlaneSummary, listDataPlanes, NewDataPlane } from '@management-commands/dataplane-provisioning-commands';

/**
 * An environment can hold at most one data plane, so the fixture adopts whatever is already bound
 * to the environment instead of failing, which keeps the spec repeatable against a persistent
 * stack (e.g. a run interrupted before its afterAll cleanup).
 */

/** Credentials the spec asserts never come back out. */
export const SECRET_PASSWORD = 'sup3r-s3cret-e2e';
export const SECRET_USERNAME = 'am-e2e-user';

export const DATABASE_NAME = 'gravitee-am-e2e-dataplane';

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
        host: 'mongodb',
        port: 27017,
        username: SECRET_USERNAME,
        password: SECRET_PASSWORD,
      },
    },
  };
}

export type ProvisionedDataPlane = {
  summary: DataPlaneSummary;
  /** False when an earlier run already bound the environment, so its stored values are not ours. */
  created: boolean;
};

/**
 * Returns the data plane bound to the default environment, provisioning it on first run. Tolerates
 * both conflict shapes: the same id already existing, and the environment already being taken.
 */
export async function ensureProvisionedDataPlane(id: string): Promise<ProvisionedDataPlane> {
  const created = await createDataPlane(dataPlanePayload(id));
  if (created.status === 201) {
    return { summary: created.body, created: true };
  }
  if (created.status !== 409) {
    throw new Error(`Unable to provision a data plane: status=${created.status} body=${created.raw}`);
  }

  const listed = await listDataPlanes();
  const bound = listed.body?.find((dataPlane) => dataPlane.environmentId === ENVIRONMENT_ID);
  if (!bound) {
    throw new Error(`Create returned 409 but no data plane is bound to environment ${ENVIRONMENT_ID}: ${listed.raw}`);
  }
  return { summary: bound, created: false };
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
