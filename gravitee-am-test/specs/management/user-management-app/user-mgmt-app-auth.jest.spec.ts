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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { UserManagementAppFixture, setupUserManagementAppFixture, CONSTANTS } from './fixtures/user-management-app-fixture';

setup();

let fixture: UserManagementAppFixture;
let userId: string;
let clientId: string;
let clientSecret: string;

beforeAll(async () => {
  fixture = await setupUserManagementAppFixture();

  // Create a role with 'read' permission — will be assigned directly to user
  const readRole = await fixture.createRole('auth-read-role', 'Role with read permission');
  await fixture.updateRole(readRole.id, {
    name: 'auth-read-role',
    description: 'Role with read permission',
    permissions: ['read'],
  });

  // Create a role with 'write' permission — will be assigned to group
  const writeRole = await fixture.createRole('auth-write-role', 'Role with write permission');
  await fixture.updateRole(writeRole.id, {
    name: 'auth-write-role',
    description: 'Role with write permission',
    permissions: ['write'],
  });

  // Create the user
  const user = await fixture.createUser({
    firstName: 'Jensen',
    lastName: 'Barbara',
    username: 'jensen.barbara',
    email: 'jensen.barbara@mail.com',
    password: CONSTANTS.USER_PASSWORD,
  });
  userId = user.id;

  // Assign 'read' role directly to user
  await fixture.addRolesToUser(userId, [readRole.id]);

  // Create a group, add user as member, assign 'write' role to group
  const group = await fixture.createGroup('auth-test-group');
  await fixture.updateGroup(group.id, { name: 'auth-test-group', members: [userId] });
  await fixture.addRolesToGroup(group.id, [writeRole.id]);

  // Create and configure the application with password grant + enhanceScopesWithUserPermissions
  const app = await fixture.createAndConfigureApp(fixture.defaultIdpId);
  clientId = app.clientId;
  clientSecret = app.clientSecret;
});

afterAll(async () => {
  expect(fixture).toBeDefined();
  await fixture.cleanUp();
});

describe('Authenticate User', () => {
  it('should reject authentication with wrong username', async () => {
    const response = await fixture.requestPasswordGrant(clientId, clientSecret, 'username', CONSTANTS.USER_PASSWORD);
    expect(response.status).toBe(400);
    expect(response.body.error).toEqual('invalid_grant');
    expect(response.body.error_description).toEqual('The credentials entered are invalid');
  });

  it('should reject authentication with wrong password', async () => {
    const response = await fixture.requestPasswordGrant(clientId, clientSecret, 'jensen.barbara', 'wrong-password');
    expect(response.status).toBe(400);
    expect(response.body.error).toEqual('invalid_grant');
    expect(response.body.error_description).toEqual('The credentials entered are invalid');
  });

  it('should authenticate successfully with enhanced scopes', async () => {
    const response = await fixture.requestPasswordGrant(
      clientId,
      clientSecret,
      'jensen.barbara',
      CONSTANTS.USER_PASSWORD,
      'read openid write',
    );
    expect(response.status).toBe(200);
    expect(response.body.access_token).toBeDefined();
    expect(response.body.scope).toBeDefined();
    expect(response.body.scope).toEqual('read openid write');
  });

  it('should disable user', async () => {
    const updated = await fixture.updateUserStatus(userId, false);
    expect(updated.enabled).toBe(false);
    await waitForDomainSync(fixture.domain.id);
  });

  it('should reject authentication for disabled user', async () => {
    const response = await fixture.requestPasswordGrant(clientId, clientSecret, 'jensen.barbara', CONSTANTS.USER_PASSWORD);
    expect(response.status).toBe(400);
    expect(response.body.error).toEqual('invalid_grant');
    expect(response.body.error_description).toEqual('Account is disabled for user jensen.barbara');
  });

  it('should re-enable user', async () => {
    const updated = await fixture.updateUserStatus(userId, true);
    expect(updated.enabled).toBe(true);
    await waitForDomainSync(fixture.domain.id);
  });

  it('should reset user password', async () => {
    await fixture.resetUserPassword(userId, CONSTANTS.NEW_PASSWORD);
  });

  it('should authenticate successfully after password reset', async () => {
    const response = await fixture.requestPasswordGrant(clientId, clientSecret, 'jensen.barbara', CONSTANTS.NEW_PASSWORD);
    expect(response.status).toBe(200);
    expect(response.body.access_token).toBeDefined();
  });

  it('should reject deleting unknown user with 404', async () => {
    await expect(fixture.deleteUser('wrong-id')).rejects.toMatchObject({
      response: { status: 404 },
      message: expect.stringContaining('User [wrong-id] can not be found'),
    });
  });

  it('should delete the user', async () => {
    await fixture.deleteUser(userId);
    await waitForDomainSync(fixture.domain.id);
  });

  it('should reject authentication for deleted user', async () => {
    const response = await fixture.requestPasswordGrant(clientId, clientSecret, 'jensen.barbara', CONSTANTS.USER_PASSWORD);
    expect(response.status).toBe(400);
    expect(response.body.error).toEqual('invalid_grant');
    expect(response.body.error_description).toEqual('The credentials entered are invalid');
  });
});
