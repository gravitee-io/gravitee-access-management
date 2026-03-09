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
import { UserManagementAppFixture, setupUserManagementAppFixture } from './fixtures/user-management-app-fixture';

setup();

let fixture: UserManagementAppFixture;

beforeAll(async () => {
  fixture = await setupUserManagementAppFixture();
});

afterAll(async () => {
  expect(fixture).toBeDefined();
  await fixture.cleanUp();
});

describe('Roles - Create', () => {
  let roleId: string;
  let role2Id: string;

  it('should reject malformed JSON with 400', async () => {
    await expect(fixture.createRole(undefined, undefined)).rejects.toMatchObject({
      response: { status: 400 },
    });
  });

  it('should reject missing name with 400', async () => {
    await expect(fixture.createRole(undefined, 'role description')).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/name.*must not be null|name.*ne doit pas/),
    });
  });

  it('should create a role', async () => {
    const role = await fixture.createRole('role name', 'role description');
    expect(role).toBeDefined();
    expect(role.id).toBeDefined();
    expect(role.name).toEqual('role name');
    expect(role.description).toEqual('role description');
    roleId = role.id;
  });

  it('should create a second role', async () => {
    const role = await fixture.createRole('role 2 name', 'role 2 description');
    expect(role).toBeDefined();
    expect(role.id).toBeDefined();
    expect(role.name).toEqual('role 2 name');
    expect(role.description).toEqual('role 2 description');
    role2Id = role.id;
  });

  it('should reject duplicate role name with 400', async () => {
    await expect(fixture.createRole('role name', 'role description')).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('A role [role name] already exists'),
    });
  });

  describe('Roles - Update', () => {
    it('should reject unknown role with 404', async () => {
      await expect(fixture.updateRole('wrong-id', { name: 'role name 2' })).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('Role [wrong-id] can not be found'),
      });
    });

    it('should reject empty body', async () => {
      // SDK validates required params client-side; Postman sent empty body → 400
      await expect(fixture.updateRole(roleId, undefined)).rejects.toThrow();
    });

    it('should update a role', async () => {
      const updated = await fixture.updateRole(roleId, {
        name: 'role name',
        description: 'new description',
        permissions: ['read'],
      });
      expect(updated.description).toEqual('new description');
      expect(updated.permissions).toEqual(['read']);
    });

    it('should update the second role', async () => {
      const updated = await fixture.updateRole(role2Id, {
        name: 'role 2 name',
        description: 'new description 2',
        permissions: ['write'],
      });
      expect(updated.description).toEqual('new description 2');
      expect(updated.permissions).toEqual(['write']);
    });
  });

  describe('Roles - Get Single', () => {
    it('should reject unknown role with 404', async () => {
      await expect(fixture.getRole('wrong-id')).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('Role [wrong-id] can not be found'),
      });
    });

    it('should get a single role', async () => {
      const role = await fixture.getRole(roleId);
      expect(role).toBeDefined();
      expect(role.id).toEqual(roleId);
      expect(role.name).toEqual('role name');
    });
  });

  describe('Roles - List', () => {
    it('should list all roles', async () => {
      const result = await fixture.listRoles();
      expect(result).toBeDefined();
      expect(result.data.length).toEqual(2);
    });
  });

  describe('Roles - Delete', () => {
    it('should reject deleting unknown role with 404', async () => {
      await expect(fixture.deleteRole('wrong-id')).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('Role [wrong-id] can not be found'),
      });
    });

    it('should delete a role', async () => {
      await fixture.deleteRole(role2Id);
    });

    it('should reject getting deleted role with 404', async () => {
      await expect(fixture.getRole(role2Id)).rejects.toMatchObject({
        response: { status: 404 },
      });
    });

    it('should list roles after deletion', async () => {
      const result = await fixture.listRoles();
      expect(result).toBeDefined();
      expect(result.data.length).toEqual(1);
    });
  });
});
