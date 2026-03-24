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
import {
  createDomain,
  startDomain,
  waitForDomainStart,
  safeDeleteDomain,
  patchDomain,
} from '@management-commands/domain-management-commands';
import faker from 'faker';
import { createRole, updateRole, getAllRoles, deleteRole, getRole } from '@management-commands/role-management-commands';
import {
  createUser,
  getUser,
  updateUser,
  getAllUsers,
  deleteUser,
  resetUserPassword,
  addRolesToUser,
  listUserRoles,
  revokeUserRole,
  updateUserStatus,
  updateUsername,
  lockUser,
  unlockUser,
  sendRegistrationConfirmation,
} from '@management-commands/user-management-commands';
import {
  createGroup,
  getGroup,
  updateGroup,
  getAllGroups,
  deleteGroup,
  addRolesToGroup,
  revokeRoleToGroup,
  getGroupMembers,
  addGroupMember,
  removeGroupMember,
  getGroupRoles,
} from '@management-commands/group-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { User } from '@management-models/User';
import { UserEntity } from '@management-models/UserEntity';
import { UserPage } from '@management-models/UserPage';
import { Role } from '@management-models/Role';
import { RoleEntity } from '@management-models/RoleEntity';
import { RolePage } from '@management-models/RolePage';
import { Group } from '@management-models/Group';
import { GroupPage } from '@management-models/GroupPage';
import { Fixture } from '../../../test-fixture';

export interface UserManagementAppFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  defaultIdpId: string;
  openIdConfiguration: any;

  // Role helpers
  createRole: (name: string, description: string) => Promise<Role>;
  getRole: (roleId: string) => Promise<RoleEntity>;
  updateRole: (roleId: string, payload: any) => Promise<RoleEntity>;
  listRoles: () => Promise<RolePage>;
  deleteRole: (roleId: string) => Promise<void>;

  // User helpers
  createUser: (payload: any) => Promise<User>;
  getUser: (userId: string) => Promise<UserEntity>;
  updateUser: (userId: string, payload: any) => Promise<User>;
  updateUserStatus: (userId: string, enabled: boolean) => Promise<User>;
  updateUsername: (userId: string, username: string) => Promise<User>;
  listUsers: () => Promise<UserPage>;
  deleteUser: (userId: string) => Promise<void>;
  resetUserPassword: (userId: string, password: string) => Promise<void>;
  lockUser: (userId: string) => Promise<void>;
  unlockUser: (userId: string) => Promise<void>;
  sendRegistrationConfirmation: (userId: string) => Promise<void>;
  addRolesToUser: (userId: string, roles: string[]) => Promise<User>;
  listUserRoles: (userId: string) => Promise<Array<Role>>;
  revokeUserRole: (userId: string, roleId: string) => Promise<User>;

  // Group helpers
  createGroup: (name: string) => Promise<Group>;
  getGroup: (groupId: string) => Promise<Group>;
  updateGroup: (groupId: string, payload: any) => Promise<Group>;
  listGroups: () => Promise<GroupPage>;
  deleteGroup: (groupId: string) => Promise<void>;
  addRolesToGroup: (groupId: string, roles: string[]) => Promise<Group>;
  revokeRoleFromGroup: (groupId: string, roleId: string) => Promise<Group>;
  getGroupMembers: (groupId: string, page?: number, size?: number) => Promise<UserPage>;
  addGroupMember: (groupId: string, memberId: string) => Promise<void>;
  removeGroupMember: (groupId: string, memberId: string) => Promise<void>;
  getGroupRoles: (groupId: string) => Promise<Array<Role>>;

  // Domain lifecycle helpers
  startAndWaitForDomain: () => Promise<void>;

  // Application helpers
  createAndConfigureApp: (identityProviderId: string) => Promise<{ clientId: string; clientSecret: string }>;

  // Auth helpers
  requestPasswordGrant: (
    clientId: string,
    clientSecret: string,
    username: string,
    password: string,
    scope?: string,
  ) => Promise<any>;

  // Domain helpers
  patchDomain: (body: any) => Promise<any>;

  cleanUp: () => Promise<void>;
}

export const CONSTANTS = {
  DOMAIN_NAME_PREFIX: 'user-mgmt-app',
  USER_PASSWORD: '#CoMpL3X-P@SsW0Rd',
  NEW_PASSWORD: 'myNew#CoMpL3X-P@SsW0Rd',
  REDIRECT_URI: 'https://callback',
} as const;

/**
 * Options for fixture setup.
 * @param beforeStart - Callback invoked after domain creation but before domain start.
 *   Use this to create resources (e.g. applications) that should be picked up by the
 *   initial gateway sync, avoiding post-start sync race conditions.
 */
interface SetupOptions {
  beforeStart?: (fixture: UserManagementAppFixture) => Promise<void>;
}

export const setupUserManagementAppFixture = async (options?: SetupOptions): Promise<UserManagementAppFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(accessToken, uniqueName(CONSTANTS.DOMAIN_NAME_PREFIX, true), faker.company.catchPhraseDescriptor());
    expect(domain).toBeDefined();
    expect(domain.id).toBeDefined();

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();
    const defaultIdpId = defaultIdp.id;

    // oidcConfig is set after domain start; requestPasswordGrant closes over this variable
    let oidcConfig: any = null;

    const cleanUp = async () => {
      expect(domain).toBeDefined();
      expect(accessToken).toBeDefined();
      await safeDeleteDomain(domain.id, accessToken);
    };

    const fixture: UserManagementAppFixture = {
      domain,
      accessToken,
      defaultIdpId,
      get openIdConfiguration() {
        return oidcConfig;
      },

      createRole: (name, description) => createRole(domain.id, accessToken, { name, description }),
      getRole: (roleId) => getRole(domain.id, accessToken, roleId),
      updateRole: (roleId, payload) => updateRole(domain.id, accessToken, roleId, payload),
      listRoles: () => getAllRoles(domain.id, accessToken),
      deleteRole: (roleId) => deleteRole(domain.id, accessToken, roleId),

      createUser: (payload) => createUser(domain.id, accessToken, payload),
      getUser: (userId) => getUser(domain.id, accessToken, userId),
      updateUser: (userId, payload) => updateUser(domain.id, accessToken, userId, payload),
      updateUserStatus: (userId, enabled) => updateUserStatus(domain.id, accessToken, userId, enabled),
      updateUsername: (userId, username) => updateUsername(domain.id, accessToken, userId, username),
      listUsers: () => getAllUsers(domain.id, accessToken),
      deleteUser: (userId) => deleteUser(domain.id, accessToken, userId),
      resetUserPassword: (userId, password) => resetUserPassword(domain.id, accessToken, userId, password),
      lockUser: (userId) => lockUser(domain.id, accessToken, userId),
      unlockUser: (userId) => unlockUser(domain.id, accessToken, userId),
      sendRegistrationConfirmation: (userId) => sendRegistrationConfirmation(domain.id, accessToken, userId),
      addRolesToUser: (userId, roles) => addRolesToUser(domain.id, accessToken, userId, roles),
      listUserRoles: (userId) => listUserRoles(domain.id, accessToken, userId),
      revokeUserRole: (userId, roleId) => revokeUserRole(domain.id, accessToken, userId, roleId),

      createGroup: (name) => createGroup(domain.id, accessToken, { name }),
      getGroup: (groupId) => getGroup(domain.id, accessToken, groupId),
      updateGroup: (groupId, payload) => updateGroup(domain.id, accessToken, groupId, payload),
      listGroups: () => getAllGroups(domain.id, accessToken),
      deleteGroup: (groupId) => deleteGroup(domain.id, accessToken, groupId),
      addRolesToGroup: (groupId, roles) => addRolesToGroup(domain.id, accessToken, groupId, roles),
      revokeRoleFromGroup: (groupId, roleId) => revokeRoleToGroup(domain.id, accessToken, groupId, roleId),
      getGroupMembers: (groupId, page?, size?) => getGroupMembers(domain.id, accessToken, groupId, page, size),
      addGroupMember: (groupId, memberId) => addGroupMember(domain.id, accessToken, groupId, memberId),
      removeGroupMember: (groupId, memberId) => removeGroupMember(domain.id, accessToken, groupId, memberId),
      getGroupRoles: (groupId) => getGroupRoles(domain.id, accessToken, groupId),

      startAndWaitForDomain: async () => {
        await startDomain(domain.id, accessToken);
        const { oidcConfig: oidc } = await waitForDomainStart(domain);
        oidcConfig = oidc;
      },

      createAndConfigureApp: async (identityProviderId: string) => {
        const app = await createTestApp(uniqueName('client-um', true), domain, accessToken, 'WEB', {
          settings: {
            oauth: {
              redirectUris: [CONSTANTS.REDIRECT_URI],
              grantTypes: ['password'],
              scopeSettings: [{ scope: 'openid', defaultScope: true }],
              enhanceScopesWithUserPermissions: true,
            },
          },
          identityProviders: new Set([{ identity: identityProviderId, priority: -1 }]),
        });

        return {
          clientId: app.settings.oauth.clientId,
          clientSecret: app.settings.oauth.clientSecret,
        };
      },

      requestPasswordGrant: async (clientId, clientSecret, username, password, scope?) => {
        expect(oidcConfig).toBeDefined();
        const tokenEndpoint = oidcConfig.token_endpoint;
        let body = `grant_type=password&username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
        if (scope) {
          body += `&scope=${encodeURIComponent(scope)}`;
        }
        return performPost(tokenEndpoint, '', body, {
          'Content-Type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(clientId, clientSecret),
        });
      },

      patchDomain: (body) => patchDomain(domain.id, accessToken, body),

      cleanUp,
    };

    // Allow callers to create resources before domain start (e.g. apps that need to be
    // picked up by the initial sync, avoiding post-start sync race conditions).
    if (options?.beforeStart) {
      await options.beforeStart(fixture);
    }

    // Start domain and wait for gateway readiness
    await startDomain(domain.id, accessToken);
    const { oidcConfig: startedOidcConfig } = await waitForDomainStart(domain);
    oidcConfig = startedOidcConfig;

    return fixture;
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
