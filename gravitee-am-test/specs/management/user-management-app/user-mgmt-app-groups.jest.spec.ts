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
import { UserManagementAppFixture, setupUserManagementAppFixture, CONSTANTS } from './fixtures/user-management-app-fixture';

setup();

let fixture: UserManagementAppFixture;
let userId: string;
let roleId: string;

beforeAll(async () => {
  fixture = await setupUserManagementAppFixture();

  // Create a user to use as a group member
  const user = await fixture.createUser({
    firstName: 'GroupMember',
    lastName: 'User',
    username: 'groupmember.user',
    email: 'groupmember.user@mail.com',
    password: CONSTANTS.USER_PASSWORD,
  });
  userId = user.id;

  // Create a role to assign to groups
  const role = await fixture.createRole('group-role', 'Role for group assignment');
  roleId = role.id;
});

afterAll(async () => {
  expect(fixture).toBeDefined();
  await fixture.cleanUp();
});

describe('Groups - Create', () => {
  let groupId: string;
  let group2Id: string;

  it('should reject missing name with 400', async () => {
    await expect(fixture.createGroup(undefined)).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/name.*must not be null|name.*ne doit pas/),
    });
  });

  it('should create a group', async () => {
    const group = await fixture.createGroup('My Group');
    expect(group).toBeDefined();
    expect(group.id).toBeDefined();
    expect(group.name).toEqual('My Group');
    groupId = group.id;
  });

  it('should create a second group', async () => {
    const group = await fixture.createGroup('My Group 2');
    expect(group).toBeDefined();
    expect(group.id).toBeDefined();
    expect(group.name).toEqual('My Group 2');
    group2Id = group.id;
  });

  it('should reject duplicate group name with 400', async () => {
    await expect(fixture.createGroup('My Group')).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('A group [My Group] already exists'),
    });
  });

  describe('Groups - Get Single', () => {
    it('should reject unknown group with 404', async () => {
      await expect(fixture.getGroup('wrong-id')).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('Group [wrong-id] can not be found'),
      });
    });

    it('should get a single group', async () => {
      const group = await fixture.getGroup(groupId);
      expect(group).toBeDefined();
      expect(group.id).toEqual(groupId);
      expect(group.name).toEqual('My Group');
    });
  });

  describe('Groups - Update', () => {
    it('should reject unknown group with 404', async () => {
      await expect(fixture.updateGroup('wrong-id', { name: 'My Group' })).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('Group [wrong-id] can not be found'),
      });
    });

    it('should reject empty body', async () => {
      // SDK validates required params client-side; Postman sent empty body → 400
      await expect(fixture.updateGroup(groupId, undefined)).rejects.toThrow();
    });

    it('should update a group', async () => {
      const updated = await fixture.updateGroup(groupId, { name: 'My New Group' });
      expect(updated.name).toEqual('My New Group');
    });
  });

  describe('Groups - Members', () => {
    it('should silently ignore unknown member', async () => {
      const updated = await fixture.updateGroup(groupId, {
        name: 'My New Group',
        members: ['wrong-member-id'],
      });
      expect(updated.members).toEqual([]);
    });

    it('should add a member to the group', async () => {
      const updated = await fixture.updateGroup(groupId, {
        name: 'My New Group',
        members: [userId],
      });
      expect(updated.members).toEqual([userId]);
    });
  });

  describe('Groups - Members (dedicated endpoints)', () => {
    it('should remove existing member to start clean', async () => {
      await fixture.removeGroupMember(groupId, userId);
      const result = await fixture.getGroupMembers(groupId);
      expect(result.totalCount).toEqual(0);
      expect(result.data).toEqual([]);
    });

    it('should add a member via dedicated endpoint', async () => {
      await fixture.addGroupMember(groupId, userId);
      const result = await fixture.getGroupMembers(groupId);
      expect(result.totalCount).toEqual(1);
      expect(result.data.length).toEqual(1);
      expect(result.data[0].id).toEqual(userId);
    });

    it('should remove a member via dedicated endpoint', async () => {
      await fixture.removeGroupMember(groupId, userId);
      const result = await fixture.getGroupMembers(groupId);
      expect(result.totalCount).toEqual(0);
      expect(result.data).toEqual([]);
    });

    it('should re-add member via update for subsequent tests', async () => {
      const updated = await fixture.updateGroup(groupId, {
        name: 'My New Group',
        members: [userId],
      });
      expect(updated.members).toEqual([userId]);
    });
  });

  describe('Groups - Assign Roles', () => {
    it('should reject assigning unknown role with 404', async () => {
      await expect(fixture.addRolesToGroup(groupId, ['wrong-role-id'])).rejects.toMatchObject({
        response: { status: 404 },
      });
    });

    it('should assign a role to the group', async () => {
      const result = await fixture.addRolesToGroup(groupId, [roleId]);
      expect(result).toBeDefined();
      expect(result.roles).toEqual([roleId]);
    });
  });

  describe('Groups - List and Revoke Roles', () => {
    it('should list group roles', async () => {
      const roles = await fixture.getGroupRoles(groupId);
      expect(roles).toBeDefined();
      expect(Array.isArray(roles)).toBe(true);
      expect(roles.some((r: any) => r.id === roleId)).toBe(true);
    });

    it('should revoke a role from group', async () => {
      await fixture.revokeRoleFromGroup(groupId, roleId);
      const roles = await fixture.getGroupRoles(groupId);
      expect(roles.some((r: any) => r.id === roleId)).toBe(false);
    });

    it('should reject revoking unknown role with 404', async () => {
      await expect(fixture.revokeRoleFromGroup(groupId, 'wrong-role-id')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });

  describe('Groups - List', () => {
    it('should list groups', async () => {
      const result = await fixture.listGroups();
      expect(result).toBeDefined();
      expect(result.currentPage).toEqual(0);
      expect(result.totalCount).toEqual(2);
      expect(result.data.length).toEqual(2);
    });
  });

  describe('Groups - Delete', () => {
    it('should reject deleting unknown group with 404', async () => {
      await expect(fixture.deleteGroup('wrong-id')).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('Group [wrong-id] can not be found'),
      });
    });

    it('should delete a group', async () => {
      await fixture.deleteGroup(group2Id);
    });

    it('should list groups after deletion', async () => {
      const result = await fixture.listGroups();
      expect(result).toBeDefined();
      expect(result.currentPage).toEqual(0);
      expect(result.totalCount).toEqual(1);
      expect(result.data.length).toEqual(1);
    });
  });
});
