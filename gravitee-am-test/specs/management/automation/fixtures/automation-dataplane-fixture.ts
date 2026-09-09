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
import { expect } from '@jest/globals';
import { requestAccessToken, requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createOrganisationUser, deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { createCustomOrganizationRole, deleteOrganizationRole } from '@management-commands/role-management-commands';
import { addOrganizationMembership, userMembership } from '@management-commands/membership-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { Fixture } from '../../../test-fixture';
import { AutomationClient } from './automation-client';

const mongoUri = new URL(process.env.AM_INTERNAL_MONGODB_URI ?? process.env.AM_MONGODB_URI);
const MONGO_HOST = mongoUri.hostname;
const MONGO_PORT = Number(mongoUri.port);
const JDBC_HOST = process.env.AM_INTERNAL_POSTGRES_HOST ?? process.env.AM_POSTGRES_HOST;
const JDBC_PORT = 5432;

export const SECRET_USERNAME = 'am-e2e-user';
export const SECRET_PASSWORD = 'sup3r-s3cret-e2e';

/** Every field a read is allowed to return. Anything else is a leak, `configuration` above all. */
export const READ_FIELDS = [
  'id',
  'name',
  'type',
  'gatewayUrl',
  'database',
  'hosts',
  'organizationId',
  'environmentId',
  'createdAt',
  'updatedAt',
];

/** Provisioning validates the shape only, so the fake credentials double as the leak assertions' needles. */
export const dataPlanePayload = (id: string, overrides: Record<string, unknown> = {}) => ({
  id,
  name: 'E2E automation data plane',
  type: 'mongodb',
  gatewayUrl: process.env.AM_GATEWAY_URL,
  configuration: {
    mongodb: {
      dbname: 'gravitee-am-e2e-dataplane',
      host: MONGO_HOST,
      port: MONGO_PORT,
      username: SECRET_USERNAME,
      password: SECRET_PASSWORD,
    },
  },
  ...overrides,
});

/** The other type, for the immutable-`type` case. */
export const jdbcPayload = (id: string) => ({
  id,
  name: 'E2E automation data plane',
  type: 'jdbc',
  configuration: {
    jdbc: {
      driver: 'postgresql',
      host: JDBC_HOST,
      port: JDBC_PORT,
      database: 'gravitee-am-e2e-dataplane',
      username: SECRET_USERNAME,
      password: SECRET_PASSWORD,
    },
  },
});

/**
 * A data plane whose store the management API can actually reach, so the registry verifies it and a
 * domain can be bound to it.
 */
export const connectablePayload = (id: string) =>
  process.env.REPOSITORY_TYPE === 'jdbc'
    ? {
        id,
        name: 'E2E connectable automation data plane',
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
        name: 'E2E connectable automation data plane',
        type: 'mongodb',
        configuration: { mongodb: { dbname: 'gravitee-am', host: MONGO_HOST, port: MONGO_PORT } },
      };

export interface AutomationDataPlaneFixture extends Fixture {
  /** Admin JWT. The admin is ORGANIZATION_PRIMARY_OWNER. */
  adminToken: string;
  adminClient: AutomationClient;
  /** Client acting as a user holding the custom data-plane role. */
  client: AutomationClient;
  /** Mints a unique id and registers it for cleanup. */
  reserveId: (prefix?: string) => string;
}

/** Flattened permissions the Automation API's data plane endpoints require. */
export const DATA_PLANE_PERMISSIONS = [
  'data_plane_managed_create',
  'data_plane_managed_update',
  'data_plane_managed_delete',
  'data_plane_read',
  'data_plane_list',
];

const PASSWORD = 'DataPl@neP@ssw0rd1!';

/**
 * Ids are tracked per fixture instance, never swept from the environment: jest runs spec files on
 * separate workers, and a sweep would delete data planes another worker is still bound to.
 */
export const setupAutomationDataPlaneFixture = async (): Promise<AutomationDataPlaneFixture> => {
  const adminToken = await requestAdminAccessToken();
  expect(adminToken).toMatch(JWT_FORMAT);

  const reserved: string[] = [];
  let roleId: string | undefined;
  let userId: string | undefined;

  try {
    const role = await createCustomOrganizationRole(
      adminToken,
      uniqueName('dataplane-operator', true),
      'ORGANIZATION',
      DATA_PLANE_PERMISSIONS,
    );
    roleId = role.id;

    const username = uniqueName(`dpop-${process.pid}`, true).toLowerCase();
    const user = await createOrganisationUser(adminToken, {
      firstName: 'DataPlane',
      lastName: 'Operator',
      email: `${username}@test.com`,
      username,
      password: PASSWORD,
      preRegistration: false,
    });
    userId = user.id;
    await addOrganizationMembership(adminToken, userMembership(user.id, role.id));

    const operatorToken = await requestAccessToken(username, PASSWORD);
    expect(operatorToken).toMatch(JWT_FORMAT);

    const client = new AutomationClient(operatorToken);

    return {
      adminToken,
      adminClient: new AutomationClient(adminToken),
      client,
      reserveId: (prefix = 'dp-auto') => {
        const id = uniqueName(`${prefix}-${process.pid}`, true).toLowerCase();
        reserved.push(id);
        return id;
      },
      cleanUp: async () => {
        for (const id of reserved) {
          await client.deleteDataPlane(id).catch(() => undefined);
        }
        if (userId) {
          await deleteOrganisationUser(adminToken, userId).catch(() => undefined);
        }
        if (roleId) {
          await deleteOrganizationRole(adminToken, roleId).catch(() => undefined);
        }
      },
    };
  } catch (error) {
    if (userId) {
      await deleteOrganisationUser(adminToken, userId).catch(() => undefined);
    }
    if (roleId) {
      await deleteOrganizationRole(adminToken, roleId).catch(() => undefined);
    }
    throw error;
  }
};
