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
import { getDomainApi, getUserApi } from './service/utils';
import { expect } from '@jest/globals';
import { NewOrganizationUser, NewUser } from '../../management/models';

export const createUser = (domainId, accessToken, user) =>
  getUserApi(accessToken).createUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    newUser: user,
  });

export const bulkCreateOrgUsers = (accessToken: string, users: NewOrganizationUser[]) =>
  getUserApi(accessToken).bulkOrganisationUserOperation({
    organizationId: process.env.AM_DEF_ORG_ID,
    organizationUserBulkRequest: {
      action: 'CREATE',
      items: users,
    },
  });

export const bulkCreateUsers = (domainId: string, accessToken: string, users: NewUser[], failOnErrors = 0) =>
  getUserApi(accessToken).bulkUserOperation({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    domainUserBulkRequest: {
      action: 'CREATE',
      items: users,
      failOnErrors: failOnErrors,
    },
  });

export const getUser = (domainId, accessToken, userId: string) =>
  getUserApi(accessToken).findUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });

export const getUserFactors = (domainId, accessToken, userId: string) =>
  getUserApi(accessToken).getUsersEnrolledFactors({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });

export const getAllUsers = (domainId, accessToken) => getUserPage(domainId, accessToken);

export const getUserPage = (domainId, accessToken, page: number = null, size: number = null) => {
  const params = {
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
  };
  if (page !== null && size != null) {
    return getUserApi(accessToken).listUsers({ ...params, page: page, size: size });
  }
  return getUserApi(accessToken).listUsers(params);
};

export const updateUser = (domainId, accessToken, userId, payload) =>
  getUserApi(accessToken).updateUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
    updateUser: payload,
  });

export const updateUserStatus = (domainId, accessToken, userId, status: boolean) =>
  getUserApi(accessToken).updateUserStatus({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
    statusEntity: { enabled: status },
  });

export const updateUsername = (domainId, accessToken, userId, username) =>
  getUserApi(accessToken).updateUsername({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
    usernameEntity: { username: username },
  });

export const resetUserPassword = (domainId, accessToken, userId, password) =>
  getUserApi(accessToken).resetPassword({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
    passwordValue: { password: password },
  });

export const sendRegistrationConfirmation = (domainId, accessToken, userId) =>
  getUserApi(accessToken).sendRegistrationConfirmation({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });

export const lockUser = (domainId, accessToken, userId) =>
  getUserApi(accessToken).lockUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });

export const unlockUser = (domainId, accessToken, userId) =>
  getUserApi(accessToken).unlockUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });

export const deleteUser = (domainId, accessToken, userId) =>
  getUserApi(accessToken).deleteUser({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });

export function buildTestUser(
  i: number,
  options: { preRegistration?: boolean; password?: string; serviceAccount?: boolean; lastPasswordReset?: Date } = {},
) {
  const { preRegistration = false, password = 'SomeP@assw0rd', serviceAccount = false } = options;
  const firstName = 'firstName' + i;
  const lastName = 'lastName' + i;
  return {
    firstName: firstName,
    lastName: lastName,
    email: `${firstName}.${lastName}@example.com`,
    username: `${firstName}.${lastName}`,
    password: serviceAccount ? undefined : password,
    preRegistration: preRegistration,
    serviceAccount: serviceAccount,
    lastPasswordReset: options.lastPasswordReset,
  };
}

export async function buildCreateAndTestUser(
  domainId,
  accessToken,
  i: number,
  preRegistration: boolean = false,
  password = 'SomeP@ssw0rd',
  lastPasswordReset?: Date,
) {
  const payload = buildTestUser(i, { preRegistration, password, lastPasswordReset });

  const newUser = await createUser(domainId, accessToken, payload);
  expect(newUser).toBeDefined();
  expect(newUser.id).toBeDefined();
  expect(newUser.firstName).toEqual(payload.firstName);
  expect(newUser.lastName).toEqual(payload.lastName);
  expect(newUser.username).toEqual(payload.username);
  expect(newUser.email).toEqual(payload.email);
  expect(newUser.password).not.toBeDefined();
  newUser.password = password; //Recreate password.
  return newUser;
}

export const listUsers = (domainId, accessToken, query) =>
  getDomainApi(accessToken).listUsers({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    q: query,
  });
