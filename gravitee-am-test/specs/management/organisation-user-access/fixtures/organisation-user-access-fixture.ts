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
import { addOrganizationMembership, userMembership } from '@management-commands/membership-management-commands';
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { User } from '@management-models/User';
import { automationUrl, envPath } from '../../automation/fixtures/automation-client';

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
  /** An organization user holding the ORGANIZATION_OWNER role. */
  createAdministrator: () => Promise<User>;
  /** A service account (no password, cannot sign in) holding the ORGANIZATION_OWNER role. */
  createServiceAccount: () => Promise<User>;
  deleteUser: (userId: string) => Promise<void>;
  login: (username: string, password: string) => Promise<string>;
  loginStatus: (username: string, password: string) => Promise<number>;
  currentUserStatus: (bearer: string) => Promise<number>;
  /** Status of an Automation API call that needs DOMAIN[LIST], which ORGANIZATION_OWNER grants. */
  automationStatus: (bearer: string) => Promise<number>;
  createAccountToken: (userId: string) => Promise<string>;
  createAccountTokenWithId: (userId: string) => Promise<{ tokenId: string; token: string }>;
  revokeAccountToken: (userId: string, tokenId: string) => Promise<void>;
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
  const organizationOwnerRole = await findOrganizationRoleByName(adminAccessToken, 'ORGANIZATION_OWNER', ListRolesTypeEnum.Organization);

  const createOrganizationUser = async (serviceAccount: boolean): Promise<User> => {
    const username = uniqueName(ORG_USER_ACCESS_TEST.USER_PREFIX, true).toLowerCase();
    const user = await createOrganisationUser(adminAccessToken, {
      username,
      firstName: 'Org',
      lastName: 'Access',
      email: `${username}@mail.com`,
      password: serviceAccount ? undefined : ORG_USER_ACCESS_TEST.PASSWORD,
      preRegistration: false,
      serviceAccount,
    });
    createdUserIds.push(user.id);
    return user;
  };

  const createUser = (): Promise<User> => createOrganizationUser(false);

  const grantOrganizationOwner = async (user: User): Promise<User> => {
    await addOrganizationMembership(adminAccessToken, userMembership(user.id, organizationOwnerRole.id));
    return user;
  };

  const createAdministrator = (): Promise<User> => createOrganizationUser(false).then(grantOrganizationOwner);

  const createServiceAccount = (): Promise<User> => createOrganizationUser(true).then(grantOrganizationOwner);

  const deleteUser = async (userId: string): Promise<void> => {
    await deleteOrganisationUser(adminAccessToken, userId);
    createdUserIds.splice(createdUserIds.indexOf(userId), 1);
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

  const automationStatus = async (bearer: string): Promise<number> => {
    const response = await performGet(automationUrl(), `${envPath()}/domains`, {
      Authorization: `Bearer ${bearer}`,
    });
    return response.status;
  };

  const createAccountTokenWithId = async (userId: string): Promise<{ tokenId: string; token: string }> => {
    const token = await getUserApi(adminAccessToken).createAccountAccessToken({
      organizationId,
      user: userId,
      newAccountAccessToken: { name: uniqueName('token', true) },
    });
    return { tokenId: token.tokenId, token: token.token };
  };

  const createAccountToken = (userId: string): Promise<string> => createAccountTokenWithId(userId).then(({ token }) => token);

  const revokeAccountToken = async (userId: string, tokenId: string): Promise<void> => {
    await getUserApi(adminAccessToken).revokeAccountAccessToken({ organizationId, user: userId, tokenId });
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
    createAdministrator,
    createServiceAccount,
    deleteUser,
    login: requestAccessToken,
    loginStatus,
    currentUserStatus,
    automationStatus,
    createAccountToken,
    createAccountTokenWithId,
    revokeAccountToken,
    listAccountTokens,
    updateStatus,
    updateProfile,
    getUser,
    resetPassword,
    cleanup,
  };
};
