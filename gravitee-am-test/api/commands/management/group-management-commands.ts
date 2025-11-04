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
