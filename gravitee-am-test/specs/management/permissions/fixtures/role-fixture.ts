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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createOrganizationRole, deleteOrganizationRole, findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { uniqueName } from '@utils-commands/misc';
import type { RoleEntity } from '@management-models/RoleEntity';
import { JWT_FORMAT } from '@specs-utils/jwt-format';

export type AssignableType = 'ORGANIZATION' | 'ENVIRONMENT' | 'DOMAIN' | 'APPLICATION';

export interface RoleFixture {
  adminToken: string;
  /** Creates a uniquely named custom role and registers it for deletion. */
  createRole: (label: string, assignableType?: AssignableType) => Promise<RoleEntity>;
  cleanup: () => Promise<void>;
}

/**
 * The role-only counterpart to `setupRbacFixture`, for specs that exercise roles themselves rather
 * than what a role grants. Deliberately provisions nothing else: the heavier fixture builds two
 * domains, two applications and four personas, none of which a role lifecycle test touches.
 *
 * Creation is two calls, not one — the create endpoint rejects a `permissions` property outright,
 * and returns no body, so the role is read back by name to recover its id.
 */
export const setupRoleFixture = async (): Promise<RoleFixture> => {
  const adminToken = await requestAdminAccessToken();
  expect(adminToken).toMatch(JWT_FORMAT);

  const createdRoleIds: string[] = [];

  const createRole = async (label: string, assignableType: AssignableType = 'DOMAIN'): Promise<RoleEntity> => {
    const name = uniqueName(label, true);
    await createOrganizationRole(adminToken, { name, assignableType });
    const role = await findOrganizationRoleByName(adminToken, name);
    createdRoleIds.push(role.id);
    return role;
  };

  const cleanup = async () => {
    for (const id of createdRoleIds) {
      // Best effort: a role a test deleted on purpose must not fail the teardown.
      await deleteOrganizationRole(adminToken, id).catch(() => undefined);
    }
  };

  return { adminToken, createRole, cleanup };
};
