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
import { getUserApi } from '@management-commands/service/utils';

export const createOrganisationUser = (accessToken, user) =>
  getUserApi(accessToken).createOrganisationUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    newOrganizationUser: user,
  });

export const getOrganisationUserPage = (accessToken, page: number = null, size: number = null) => {
  const params = {
    organizationId: process.env.AM_DEF_ORG_ID,
  };
  if (page !== null && size != null) {
    return getUserApi(accessToken).listOrganisationUsers({ ...params, page: page, size: size });
  }
  return getUserApi(accessToken).listOrganisationUsers(params);
};

export const updateOrganisationUsername = (accessToken, userId, username) =>
  getUserApi(accessToken).updateOrganisationUsername({
    organizationId: process.env.AM_DEF_ORG_ID,
    user: userId,
    usernameEntity: { username: username },
  });

export const deleteOrganisationUser = (accessToken, userId) =>
  getUserApi(accessToken).deleteOrganizationUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    user: userId,
  });

export const getCurrentUser = (accessToken) => getUserApi(accessToken).getCurrentUser();
