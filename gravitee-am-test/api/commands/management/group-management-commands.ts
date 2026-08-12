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

import { getGroupApi } from './service/utils';

export const createGroup = (domainId, accessToken, group) =>
  getGroupApi(accessToken).createGroup({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newGroup: group,
  });

export const getGroup = (domainId, accessToken, groupId) =>
  getGroupApi(accessToken).findGroup({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
  });

export const getGroupPage = (domainId, accessToken, page: number = null, size: number = null) => {
  const params = {
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  };
  if (page !== null && size != null) {
    return getGroupApi(accessToken).listDomainGroups({ ...params, page: page, size: size });
  }
  return getGroupApi(accessToken).listDomainGroups(params);
};

export const getAllGroups = (domainId, accessToken) => getGroupPage(domainId, accessToken);

export const updateGroup = (domainId, accessToken, groupId, payload) =>
  getGroupApi(accessToken).updateGroup({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
    updateGroup: payload,
  });

export const deleteGroup = (domainId, accessToken, groupId) =>
  getGroupApi(accessToken).deleteGroup({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
  });

export const addRolesToGroup = (domainId, accessToken, groupId, roles: Array<string>) =>
  getGroupApi(accessToken).assignRoles({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
    requestBody: roles,
  });

export const revokeRoleToGroup = (domainId, accessToken, groupId, role) =>
  getGroupApi(accessToken).revokeRole({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
    role: role,
  });

export const getGroupMembers = (domainId: string, accessToken: string, groupId: string, page?: number, size?: number) => {
  const params: any = {
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
  };
  if (page !== undefined) params.page = page;
  if (size !== undefined) params.size = size;
  return getGroupApi(accessToken).getGroupMembers(params);
};

export const addGroupMember = (domainId: string, accessToken: string, groupId: string, memberId: string) =>
  getGroupApi(accessToken).addGroupMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
    member: memberId,
  });

export const removeGroupMember = (domainId: string, accessToken: string, groupId: string, memberId: string) =>
  getGroupApi(accessToken).removeGroupMember({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
    member: memberId,
  });

export const getGroupRoles = (domainId: string, accessToken: string, groupId: string) =>
  getGroupApi(accessToken).findGroupRoles({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    group: groupId,
  });

// --- Organization-level groups ----------------------------------------------------------------
// The commands above are domain-scoped. Console users are grouped at organization level, and a
// role assigned to such a group applies to every member of it.

/**
 * The create endpoint declares no response body, so the SDK returns `void` and the new group's id
 * has to be recovered by name — the same shape as organization role creation.
 */
export const createOrganizationGroup = async (accessToken: string, name: string) => {
  await getGroupApi(accessToken).createPlatformGroup({
    organizationId: process.env.AM_DEF_ORG_ID,
    newGroup: { name },
  });
  // listGroups() is typed as returning an array, but the endpoint answers with a page object, so
  // the generated transformer throws. Read the raw response instead of going through it.
  const listing = await getGroupApi(accessToken).listGroupsRaw({ organizationId: process.env.AM_DEF_ORG_ID, size: 1000 });
  const body = await listing.raw.json();
  const created = (body.data ?? body).find((group) => group.name === name);
  if (!created) {
    throw new Error(`Organization group "${name}" was not found after creation`);
  }
  return created;
};

export const addOrganizationGroupMember = (accessToken: string, groupId: string, memberId: string) =>
  getGroupApi(accessToken).addGroupMember1({
    organizationId: process.env.AM_DEF_ORG_ID,
    group: groupId,
    member: memberId,
  });

export const deleteOrganizationGroup = (accessToken: string, groupId: string) =>
  getGroupApi(accessToken).deleteOrganizationGroup({
    organizationId: process.env.AM_DEF_ORG_ID,
    group: groupId,
  });
