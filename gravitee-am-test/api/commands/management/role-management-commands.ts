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

import { getRoleApi } from './service/utils';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
// Type-only: `@management-models` is not mapped in the jest moduleNameMapper, so these must stay
// erasable. `@management-apis` above *is* mapped, which is why ListRolesTypeEnum can be a value.
import type { NewRole, NewRoleAssignableTypeEnum } from '@management-models/NewRole';
import type { UpdateRole } from '@management-models/UpdateRole';
import type { RoleEntity } from '@management-models/RoleEntity';

export const createRole = (domainId, accessToken, role) =>
  getRoleApi(accessToken).createRole({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newRole: role,
  });

export const getRole = (domainId, accessToken, roleId) =>
  getRoleApi(accessToken).findRole({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    role: roleId,
  });

export const getRolePage = (domainId, accessToken, page: number = null, size: number = null) => {
  let params = {
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  };
  if (page !== null && size != null) {
    return getRoleApi(accessToken).findRoles({ ...params, page: page, size: size });
  }
  return getRoleApi(accessToken).findRoles(params);
};

export const getAllRoles = (domainId, accessToken) => getRolePage(domainId, accessToken);

export const updateRole = (domainId, accessToken, roleId, payload) =>
  getRoleApi(accessToken).updateRole({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    // role path param
    role: roleId,
    //role payload
    updateRole: payload,
  });

export const deleteRole = (domainId, accessToken, roleId) =>
  getRoleApi(accessToken).deleteRole({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    role: roleId,
  });

// --- Organization-level roles ----------------------------------------------------------------
// The commands above are domain-scoped. Administrative roles (the ones assigned to console users
// at organization, environment, domain, application or protected-resource level) live on the
// organization instead, under /organizations/{organizationId}/roles.

export const createOrganizationRole = (accessToken: string, newRole: NewRole) =>
  getRoleApi(accessToken).createRole1({
    organizationId: process.env.AM_DEF_ORG_ID,
    newRole,
  });

export const getOrganizationRole = (accessToken: string, roleId: string) =>
  getRoleApi(accessToken).getRole({
    organizationId: process.env.AM_DEF_ORG_ID,
    role: roleId,
  });

export const listOrganizationRoles = (accessToken: string, type?: ListRolesTypeEnum) =>
  getRoleApi(accessToken).listRoles({
    organizationId: process.env.AM_DEF_ORG_ID,
    ...(type ? { type } : {}),
  });

export const updateOrganizationRole = (accessToken: string, roleId: string, payload: UpdateRole) =>
  getRoleApi(accessToken).updateRole1({
    organizationId: process.env.AM_DEF_ORG_ID,
    role: roleId,
    updateRole: payload,
  });

export const deleteOrganizationRole = (accessToken: string, roleId: string) =>
  getRoleApi(accessToken).deleteRole1({
    organizationId: process.env.AM_DEF_ORG_ID,
    role: roleId,
  });

/**
 * Memberships reference a role by **id**, but tests and specs name roles ("DOMAIN_OWNER",
 * "ORGANIZATION_USER"). Resolves a built-in or custom role to its entity.
 */
export const findOrganizationRoleByName = async (accessToken: string, name: string, type?: ListRolesTypeEnum): Promise<RoleEntity> => {
  const roles = await listOrganizationRoles(accessToken, type);
  const match = roles.find((role) => role.name === name);
  if (!match) {
    throw new Error(`No organization role named "${name}"${type ? ` assignable to ${type}` : ''}`);
  }
  return match;
};

/**
 * Creates a custom administrative role carrying the given permissions.
 *
 * Two API calls are required, for two separate reasons:
 *  1. Role creation does **not** accept permissions — `NewRole` has no such field and the service
 *     stores an empty permission set, so permissions must be applied by a follow-up update.
 *     A caller that skips the update ends up with a role that silently grants nothing.
 *  2. The spec declares no response body for the create, so the SDK returns `void` and the new
 *     role's id has to be recovered by name before it can be updated.
 *
 * @param permissions flattened `permission_acl` strings, e.g. ['domain_read', 'application_read']
 */
export const createCustomOrganizationRole = async (
  accessToken: string,
  name: string,
  assignableType: NewRoleAssignableTypeEnum,
  permissions: string[],
): Promise<RoleEntity> => {
  await createOrganizationRole(accessToken, { name, assignableType });
  const created = await findOrganizationRoleByName(accessToken, name);
  return updateOrganizationRole(accessToken, created.id, { name, permissions });
};
