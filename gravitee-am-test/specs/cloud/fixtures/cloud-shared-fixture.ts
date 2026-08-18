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
  cockpitSignIn,
  CockpitQueueEntry,
  sendCockpitCommand,
  waitForCockpitConnection,
  waitForCockpitReply,
} from '@cloud-commands/cockpit-commands';
import { createDataPlane, getDataPlane } from '@management-commands/dataplane-provisioning-commands';
import { CloudOrganizationFixture } from './cloud-organization-fixture';

/**
 * One organization, one environment, one data plane. The `--cloud` local stack pins its gateway to
 * data plane id {@code cloud-test} (GRAVITEE_REPOSITORIES_GATEWAY_DATAPLANE_ID), and each row in
 * data_plane_definitions carries a single environmentId, so a single row is the only shape that
 * lets every cloud spec's domains land on the gateway's DP. Cloud specs run --runInBand, so the
 * shared environment is safe to reuse across suites; per-spec state (access points, domains) is
 * reset in each fixture.
 */
const ORGANIZATION_ID = 'cloud-shared';
const ENVIRONMENT_ID = 'cloud-shared-env';
const USER_ID = 'cloud-shared-owner';
const DATA_PLANE_ID = 'cloud-test';

// The definition has to name the same store the gateway is configured with, otherwise the management
// API writes users the gateway then cannot read. Both stacks run one database, so the branch follows
// REPOSITORY_TYPE the same way the internal-api data plane fixtures do. The store details are the
// local stack's own (docker-compose.mongo.yml / docker-compose.postgres.yml), which is the only thing
// that runs the cloud stack today; the hosts take the same overrides as the internal-api fixtures for
// a stack that runs the stores elsewhere.
const MONGO_HOST = process.env.AM_DATAPLANE_MONGO_HOST ?? 'mongodb';
const JDBC_HOST = process.env.AM_DATAPLANE_JDBC_HOST ?? 'postgres';
const dataPlaneStore = () =>
  process.env.REPOSITORY_TYPE === 'jdbc'
    ? {
        type: 'jdbc',
        configuration: {
          jdbc: {
            driver: 'postgresql',
            host: JDBC_HOST,
            port: 5432,
            database: 'postgres',
            username: 'postgres',
            password: 'postgres',
          },
        },
      }
    : { type: 'mongodb', configuration: { mongodb: { dbname: 'graviteeam', host: MONGO_HOST, port: 27017 } } };

export interface CloudSharedFixture extends CloudOrganizationFixture {
  /** Id of the data plane linked to the shared environment, and the one the gateway is pinned to. */
  dataPlaneId: string;
}

let cached: Promise<CloudSharedFixture> | null = null;

/**
 * Returns the shared cloud org/env/DP, provisioning it on first call and reusing it thereafter.
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudSharedFixture = (): Promise<CloudSharedFixture> => {
  if (cached === null) {
    cached = provision();
  }
  return cached;
};

const provision = async (): Promise<CloudSharedFixture> => {
  const awaitCommand = async (type: string, payload: Record<string, any>): Promise<CockpitQueueEntry> => {
    const commandId = await sendCockpitCommand({ type, payload });
    const reply = await waitForCockpitReply(commandId);
    if (reply.commandStatus !== 'SUCCEEDED') {
      throw new Error(`Cockpit ${type} command failed: ${reply.errorDetails}`);
    }
    return reply;
  };

  const resync = (payload: Record<string, any> = {}): Promise<CockpitQueueEntry> =>
    awaitCommand('ORGANIZATION', {
      id: ORGANIZATION_ID,
      name: `Cloud shared organization ${ORGANIZATION_ID}`,
      hrids: [ORGANIZATION_ID],
      ...payload,
    });

  // Before the first command, not after: waitForCockpitReply gives up at 15s while AM's websocket
  // to the mock can take up to 30s to come up on a cold --cloud stack.
  await waitForCockpitConnection();

  await resync();
  await awaitCommand('USER', {
    id: USER_ID,
    username: USER_ID,
    firstName: 'Cloud',
    lastName: 'Owner',
    email: `${USER_ID}@example.com`,
    organizationId: ORGANIZATION_ID,
  });
  await awaitCommand('MEMBERSHIP', {
    organizationId: ORGANIZATION_ID,
    referenceType: 'ORGANIZATION',
    referenceId: ORGANIZATION_ID,
    userId: USER_ID,
    role: 'ORGANIZATION_PRIMARY_OWNER',
  });
  const syncEnvironment = (): Promise<CockpitQueueEntry> =>
    awaitCommand('ENVIRONMENT', {
      id: ENVIRONMENT_ID,
      organizationId: ORGANIZATION_ID,
      hrids: [ENVIRONMENT_ID],
      name: `Cloud shared environment ${ENVIRONMENT_ID}`,
      accessPoints: [{ target: 'GATEWAY', host: `${ENVIRONMENT_ID}.example.com` }],
    });

  await syncEnvironment();
  const grantEnvironmentOwnership = (environmentId: string): Promise<CockpitQueueEntry> =>
    awaitCommand('MEMBERSHIP', {
      organizationId: ORGANIZATION_ID,
      referenceType: 'ENVIRONMENT',
      referenceId: environmentId,
      userId: USER_ID,
      role: 'ENVIRONMENT_PRIMARY_OWNER',
    });
  await grantEnvironmentOwnership(ENVIRONMENT_ID);

  // The data plane can only be created once the environment exists: DataPlaneDefinitionServiceImpl
  // resolves environmentId and rejects an unknown one.
  const store = dataPlaneStore();
  const dpResponse = await createDataPlane({
    id: DATA_PLANE_ID,
    name: 'Cloud shared data plane',
    organizationId: ORGANIZATION_ID,
    environmentId: ENVIRONMENT_ID,
    ...store,
  });
  if (dpResponse.status === 409) {
    // A row left by an earlier run under the other REPOSITORY_TYPE points at a store the gateway is
    // not reading, and the suite then fails much later with users the gateway cannot see.
    const existing = await getDataPlane(DATA_PLANE_ID);
    if (existing.body?.type !== store.type) {
      throw new Error(
        `Data plane ${DATA_PLANE_ID} already exists with type=${existing.body?.type}, expected ${store.type}. ` +
          `Reset the stack (local-stack.sh down) before switching REPOSITORY_TYPE.`,
      );
    }
  } else if (dpResponse.status !== 201) {
    throw new Error(`Failed to provision shared data plane: status=${dpResponse.status} body=${dpResponse.raw}`);
  }

  // The ENVIRONMENT command above created the entrypoints while the environment had no linked plane,
  // so those events went to `default`. Replay it now the link exists and they route to DATA_PLANE_ID.
  await syncEnvironment();

  const accessToken = await cockpitSignIn({ sub: USER_ID, organizationId: ORGANIZATION_ID, environmentId: ENVIRONMENT_ID });

  return {
    organizationId: ORGANIZATION_ID,
    environmentId: ENVIRONMENT_ID,
    userId: USER_ID,
    accessToken,
    dataPlaneId: DATA_PLANE_ID,
    resync,
    grantEnvironmentOwnership,
    // Nothing to reset: every spec sharing this organization owns its own state and clears it itself.
    cleanup: async () => undefined,
  };
};
