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

import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createRole, deleteRole, getAllRoles, getRole, getRolePage, updateRole } from '@management-commands/role-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';

setup(200000);

let accessToken;
let domain;
let role;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('roles', true), { accessToken, waitForStart: false }).then((it) => it.domain);
});

describe('when using the roles commands', () => {
  for (let i = 0; i < 10; i++) {
    it('must create new roles ' + i, async () => {
      const payload = {
        name: 'ROLE_' + faker.name.jobTitle().toUpperCase(),
        assignableType: 'domain',
        description: faker.lorem.paragraph(),
      };
      const newRole = await createRole(domain.id, accessToken, payload);
      expect(newRole).toBeDefined();
      expect(newRole.id).toBeDefined();
      expect(newRole.name).toEqual(payload.name);
      expect(newRole.description).toEqual(payload.description);
      expect(newRole.assignableType).toEqual(payload.assignableType);

      role = newRole;
    });
  }
});

describe('after creating roles', () => {
  it('must find Role by id', async () => {
    const foundRole = await getRole(domain.id, accessToken, role.id);
    expect(foundRole).toBeDefined();
    expect(foundRole.id).toEqual(role.id);
  });

  it('must update Role', async () => {
    const payload = {
      ...role,
      name: 'another-name',
      permissions: ['READ', 'WRITE'],
      oauthScopes: ['openid', 'full_profile'],
    };
    const updatedRole = await updateRole(domain.id, accessToken, role.id, payload);
    expect(role.name === updatedRole.name).toBeFalsy();
    expect(updatedRole.permissions).toContain('READ');
    expect(updatedRole.permissions).toContain('WRITE');
  });

  it('must find all Roles', async () => {
    const applicationPage = await getAllRoles(domain.id, accessToken);

    expect(applicationPage.currentPage).toEqual(0);
    expect(applicationPage.totalCount).toEqual(10);
    expect(applicationPage.data.length).toEqual(10);
  });

  it('must find Role page', async () => {
    const rolePage = await getRolePage(domain.id, accessToken, 1, 3);

    expect(rolePage.currentPage).toEqual(1);
    expect(rolePage.totalCount).toEqual(10);
    expect(rolePage.data.length).toEqual(3);
  });

  it('must find last Role page', async () => {
    const rolePage = await getRolePage(domain.id, accessToken, 3, 3);

    expect(rolePage.currentPage).toEqual(3);
    expect(rolePage.totalCount).toEqual(10);
    expect(rolePage.data.length).toEqual(1);
  });

  it('Must delete Role', async () => {
    await deleteRole(domain.id, accessToken, role.id);
    const rolePage = await getAllRoles(domain.id, accessToken);

    expect(rolePage.currentPage).toEqual(0);
    expect(rolePage.totalCount).toEqual(9);
    expect(rolePage.data.length).toEqual(9);
    expect(rolePage.data.find((r) => r.id === role.id)).toBeFalsy();
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
