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
import { createOrganisationUser, deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { requestAccessToken, requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getOrganisationManagementUrl, getUserApi } from '@management-commands/service/utils';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { User } from '@management-models/User';

const request = require('supertest');
const btoa = require('btoa');

export const ORG_USER_ACCESS_TEST = {
  USER_PREFIX: 'org-access',
  PASSWORD: 'SomeP@ssw0rd',
  NEW_PASSWORD: 'OtherP@ssw0rd1',
} as const;

// A JWT iat has a precision of one second, so a token and a session event in the same second do not order correctly.
export const waitForNextSecond = () => new Promise((resolve) => setTimeout(resolve, 1100));

export interface OrganisationUserAccessFixture {
  adminAccessToken: string;
  createUser: () => Promise<User>;
  login: (username: string, password: string) => Promise<string>;
  loginStatus: (username: string, password: string) => Promise<number>;
  currentUserStatus: (bearer: string) => Promise<number>;
  createAccountToken: (userId: string) => Promise<string>;
  listAccountTokens: (userId: string) => Promise<any[]>;
  updateStatus: (userId: string, enabled: boolean) => Promise<User>;
  updateProfile: (userId: string, firstName: string) => Promise<User>;
  getUser: (userId: string) => Promise<User>;
  resetPassword: (userId: string, password: string) => Promise<void>;
  cleanup: () => Promise<void>;
}

export const setupOrganisationUserAccessFixture = async (): Promise<OrganisationUserAccessFixture> => {
  const adminAccessToken = await requestAdminAccessToken();
  const createdUserIds: string[] = [];
  const organizationId = process.env.AM_DEF_ORG_ID;

  const createUser = async (): Promise<User> => {
    const username = uniqueName(ORG_USER_ACCESS_TEST.USER_PREFIX, true).toLowerCase();
    const user = await createOrganisationUser(adminAccessToken, {
      username,
      firstName: 'Org',
      lastName: 'Access',
      email: `${username}@mail.com`,
      password: ORG_USER_ACCESS_TEST.PASSWORD,
      preRegistration: false,
    });
    createdUserIds.push(user.id);
    return user;
  };

  const loginStatus = (username: string, password: string): Promise<number> =>
    request(process.env.AM_MANAGEMENT_URL)
      .post('/management/auth/token')
      .set('Authorization', 'Basic ' + btoa(`${username}:${password}`))
      .then((res) => res.status);

  const currentUserStatus = async (bearer: string): Promise<number> => {
    const response = await performGet(process.env.AM_MANAGEMENT_URL, '/management/user', {
      Authorization: `Bearer ${bearer}`,
    });
    return response.status;
  };

  const createAccountToken = async (userId: string): Promise<string> => {
    const token = await getUserApi(adminAccessToken).createAccountAccessToken({
      organizationId,
      user: userId,
      newAccountAccessToken: { name: uniqueName('token', true) },
    });
    return token.token;
  };

  // the generated SDK types this endpoint as a single token, so read the list from the raw response
  const listAccountTokens = async (userId: string): Promise<any[]> => {
    const response = await performGet(getOrganisationManagementUrl(), `/users/${userId}/tokens`, {
      Authorization: `Bearer ${adminAccessToken}`,
    });
    return response.body;
  };

  const updateStatus = (userId: string, enabled: boolean): Promise<User> =>
    getUserApi(adminAccessToken).updateOrganizationUserStatus({
      organizationId,
      user: userId,
      statusEntity: { enabled },
    });

  const updateProfile = (userId: string, firstName: string): Promise<User> =>
    getUserApi(adminAccessToken).updateOrganizationUser({
      organizationId,
      user: userId,
      updateUser: { firstName },
    });

  const getUser = async (userId: string): Promise<User> => {
    const response = await performGet(getOrganisationManagementUrl(), `/users/${userId}`, {
      Authorization: `Bearer ${adminAccessToken}`,
    });
    return response.body;
  };

  const resetPassword = (userId: string, password: string): Promise<void> =>
    getUserApi(adminAccessToken).resetOrganizationUserPassword({
      organizationId,
      user: userId,
      passwordValue: { password },
    });

  const cleanup = async () => {
    for (const userId of createdUserIds) {
      try {
        await deleteOrganisationUser(adminAccessToken, userId);
      } catch (error) {
        console.error(`Failed to delete organisation user ${userId}:`, error);
      }
    }
  };

  return {
    adminAccessToken,
    createUser,
    login: requestAccessToken,
    loginStatus,
    currentUserStatus,
    createAccountToken,
    listAccountTokens,
    updateStatus,
    updateProfile,
    getUser,
    resetPassword,
    cleanup,
  };
};
