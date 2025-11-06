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
import fetch from 'cross-fetch';
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createRole, updateRole } from '@management-commands/role-management-commands';
import {
  addRolesToGroup,
  createGroup,
  deleteGroup,
  getAllGroups,
  getGroup,
  getGroupPage,
  revokeRoleToGroup,
  updateGroup,
} from '@management-commands/group-management-commands';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

jest.setTimeout(200000);

let accessToken;
let domain;
let domain2;

let user;
let role;
let group;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('groups', true), { accessToken }).then((it) => it.domain);
  domain2 = await setupDomainForTest(uniqueName('groups2', true), { accessToken }).then((it) => it.domain);
});

describe('before creating groups', () => {
  it('must create a user', async () => {
    user = await buildCreateAndTestUser(domain.id, accessToken, 1);
    expect(user).toBeDefined();
  });

  it('must create a role', async () => {
    role = await createRole(domain.id, accessToken, {
      name: 'ROLE_DOMAIN_USER',
      assignableType: 'domain',
      description: faker.lorem.paragraph(),
    });
    expect(role).toBeDefined();
    role = await updateRole(domain.id, accessToken, role.id, {
      name: role.name,
      assignableType: role.assignableType,
      description: role.description,
      permissions: ['read'],
    });
    expect(role).toBeDefined();
  });
});

describe('when creating groups', () => {
  for (let i = 0; i < 10; i++) {
    it('must create new group ' + i, async () => {
      group = await createGroup(domain.id, accessToken, {
        name: 'group ' + i,
        description: faker.lorem.paragraph(),
      });
      expect(group).toBeDefined();
      expect(group.name).toEqual('group ' + i);
      expect(group.description).toBeDefined();
    });
  }
});

describe('when groups are created', () => {
  it('must find group by id', async () => {
    const foundGroup = await getGroup(domain.id, accessToken, group.id);
    expect(foundGroup).toBeDefined();
    expect(foundGroup.id).toEqual(group.id);
  });

  it('must update group', async () => {
    const updatedGroup = await updateGroup(domain.id, accessToken, group.id, {
      ...group,
      description: 'another description',
      members: [user.id],
    });
    expect(updatedGroup).toBeDefined();
    expect(updatedGroup.id).toEqual(group.id);
    expect(updatedGroup.description).toEqual('another description');
    expect(updatedGroup.members).toContain(user.id);
    group = updatedGroup;
  });

  it('must add only members from domain', async () => {
    const user2 = await buildCreateAndTestUser(domain2.id, accessToken, 1);
    const updatedGroup = await updateGroup(domain.id, accessToken, group.id, {
      ...group,
      members: [user.id, user2.id],
    });
    expect(updatedGroup).toBeDefined();
    expect(updatedGroup.id).toEqual(group.id);
    expect(updatedGroup.description).toEqual('another description');
    expect(updatedGroup.members.length).toEqual(1);
    expect(updatedGroup.members).not.toContain(user2.id);
    group = updatedGroup;
  });

  it('must list all groups', async () => {
    const groupPage = await getAllGroups(domain.id, accessToken);

    expect(groupPage.currentPage).toEqual(0);
    expect(groupPage.totalCount).toEqual(10);
    expect(groupPage.data.length).toEqual(10);
  });

  it('must get group page', async () => {
    const groupPage = await getGroupPage(domain.id, accessToken, 1, 3);

    expect(groupPage.currentPage).toEqual(1);
    expect(groupPage.totalCount).toEqual(10);
    expect(groupPage.data.length).toEqual(3);
  });

  it('must get last group page', async () => {
    const groupPage = await getGroupPage(domain.id, accessToken, 3, 3);

    expect(groupPage.currentPage).toEqual(3);
    expect(groupPage.totalCount).toEqual(10);
    expect(groupPage.data.length).toEqual(1);
  });

  it('must add a role to group', async () => {
    const updatedGroup = await addRolesToGroup(domain.id, accessToken, group.id, [role.id]);

    expect(updatedGroup).toBeDefined();
    expect(updatedGroup.roles).toContain(role.id);
  });

  it('must revoke a role from group', async () => {
    const updatedGroup = await revokeRoleToGroup(domain.id, accessToken, group.id, role.id);

    expect(updatedGroup).toBeDefined();
    expect(updatedGroup.roles).not.toContain(role.id);
  });

  it('must delete a group', async () => {
    await deleteGroup(domain.id, accessToken, group.id);

    const remainingGroups = await getAllGroups(domain.id, accessToken);

    expect(remainingGroups.currentPage).toEqual(0);
    expect(remainingGroups.totalCount).toEqual(9);
    expect(remainingGroups.data.length).toEqual(9);
    expect(remainingGroups.data.find((g) => g.id === group.id)).toBeFalsy();
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
  if (domain2 && domain2.id) {
    await safeDeleteDomain(domain2.id, accessToken);
  }
});
