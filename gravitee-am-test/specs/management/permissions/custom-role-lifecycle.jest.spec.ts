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
import { RoleFixture, setupRoleFixture } from './fixtures/role-fixture';
import {
  createOrganizationRole,
  deleteOrganizationRole,
  findOrganizationRoleByName,
  getOrganizationRole,
  updateOrganizationRole,
} from '@management-commands/role-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';

setup(200000);

let fixture: RoleFixture;

beforeAll(async () => {
  fixture = await setupRoleFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * Creating a custom role and giving it permissions are two separate calls, and not by convention —
 * the create endpoint rejects a `permissions` property outright. A caller who assumes one call
 * ends up with a role that grants nothing, which is silent and easy to miss.
 */
describe('Custom role lifecycle - permissions are applied by a second call, never at creation', () => {
  it('should reject a permissions property supplied at creation', async () => {
    const response = await performPost(
      getOrganisationManagementUrl(),
      '/roles',
      { name: uniqueName('rejected-at-create', true), assignableType: 'DOMAIN', permissions: ['domain_read'] },
      { 'Content-Type': 'application/json', Authorization: `Bearer ${fixture.adminToken}` },
    );

    expect(response.status).toBe(400);
    expect(response.body.message).toEqual('Property [permissions] is not recognized as a valid property');
  });

  it('should create the role carrying no permissions at all', async () => {
    const role = await fixture.createRole('lifecycle-empty');

    expect(role.permissions ?? []).toEqual([]);
  });

  it('should apply permissions on update', async () => {
    const role = await fixture.createRole('lifecycle-updated');

    const updated = await updateOrganizationRole(fixture.adminToken, role.id, {
      name: role.name,
      permissions: ['domain_read'],
    });

    expect(updated.permissions).toEqual(['domain_read']);
  });
});

/**
 * A role's `assignableType` constrains where it may be assigned, but not which permissions it may
 * carry — a domain-scoped role is allowed to hold application permissions it can never exercise.
 * This is deliberate to record, because it is the precondition that makes AM-7475 reachable.
 */
describe('Custom role lifecycle - permissions are not validated against the assignable type', () => {
  it('should accept an application permission on a domain-assignable role', async () => {
    const role = await fixture.createRole('lifecycle-mismatched');

    const updated = await updateOrganizationRole(fixture.adminToken, role.id, {
      name: role.name,
      permissions: ['domain_read', 'application_read'],
    });

    expect(updated.permissions.sort()).toEqual(['application_read', 'domain_read']);
    // Case-insensitive: create and list report the assignable type lowercase, update reports it
    // uppercase. The casing is incidental here — what matters is that the type stayed DOMAIN while
    // an application permission was accepted onto it.
    expect(updated.assignableType.toLowerCase()).toEqual('domain');
  });
});

describe('Custom role lifecycle - malformed permissions are refused as client errors', () => {
  it.each<[string, string[]]>([
    ['an unknown acl', ['domain_pillage']],
    ['an unknown permission', ['kingdom_read']],
    ['no separator at all', ['read']],
    ['an empty permission half', ['_read']],
    ['an empty acl half', ['domain_']],
    ['an empty string', ['']],
    ['surrounding whitespace', [' domain_read']],
    ['a valid permission alongside an invalid one', ['domain_read', 'domain_pillage']],
  ])('should refuse %s with a 400', async (_label, permissions) => {
    const role = await fixture.createRole('lifecycle-invalid');

    await expect(updateOrganizationRole(fixture.adminToken, role.id, { name: role.name, permissions })).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Invalid permission(s)'),
    });
  });

  it('should name every rejected entry, not just the first', async () => {
    const role = await fixture.createRole('lifecycle-invalid-reported');

    await expect(
      updateOrganizationRole(fixture.adminToken, role.id, { name: role.name, permissions: ['domain_pillage', 'kingdom_read'] }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/domain_pillage.*kingdom_read/),
    });
  });

  it('should leave the permissions already applied untouched after a refused update', async () => {
    const role = await fixture.createRole('lifecycle-invalid-noop');
    await updateOrganizationRole(fixture.adminToken, role.id, { name: role.name, permissions: ['domain_read'] });

    await expect(
      updateOrganizationRole(fixture.adminToken, role.id, { name: role.name, permissions: ['domain_update', 'domain_pillage'] }),
    ).rejects.toMatchObject({ response: { status: 400 } });

    const reread = await getOrganizationRole(fixture.adminToken, role.id);
    expect(reread.permissions).toEqual(['domain_read']);
  });
});

describe('Custom role lifecycle - naming and deletion', () => {
  it('should refuse a second role with a name already in use', async () => {
    const role = await fixture.createRole('lifecycle-duplicate');

    await expect(createOrganizationRole(fixture.adminToken, { name: role.name, assignableType: 'DOMAIN' })).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('already exists'),
    });
  });

  it('should delete a custom role and stop resolving it afterwards', async () => {
    const role = await fixture.createRole('lifecycle-deleted');

    await deleteOrganizationRole(fixture.adminToken, role.id);

    await expect(getOrganizationRole(fixture.adminToken, role.id)).rejects.toMatchObject({ response: { status: 404 } });
  });
});

/**
 * Built-in roles are protected from edits that would change what the product itself relies on.
 * System roles are not addressable through the organization endpoint at all, so the refusal
 * surfaces as a 404 rather than the 400 the service's own guard would produce; either way the
 * edit does not happen, which is the property worth holding.
 */
describe('Custom role lifecycle - built-in roles resist modification', () => {
  it('should refuse to update a system role', async () => {
    const systemRole = await findOrganizationRoleByName(fixture.adminToken, 'ORGANIZATION_PRIMARY_OWNER');

    await expect(
      updateOrganizationRole(fixture.adminToken, systemRole.id, { name: 'ORGANIZATION_PRIMARY_OWNER', permissions: [] }),
    ).rejects.toMatchObject({ response: { status: 404 } });
  });

  it('should refuse to delete a system role', async () => {
    const systemRole = await findOrganizationRoleByName(fixture.adminToken, 'ORGANIZATION_PRIMARY_OWNER');

    await expect(deleteOrganizationRole(fixture.adminToken, systemRole.id)).rejects.toMatchObject({ response: { status: 404 } });
  });

  it('should refuse to rename a default role', async () => {
    const defaultRole = await findOrganizationRoleByName(fixture.adminToken, 'DOMAIN_USER', ListRolesTypeEnum.Domain);

    await expect(
      updateOrganizationRole(fixture.adminToken, defaultRole.id, { name: 'RENAMED_DOMAIN_USER', permissions: ['domain_read'] }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('name cannot be updated'),
    });
  });

  it('should leave the default role untouched after the rejected rename', async () => {
    const defaultRole = await findOrganizationRoleByName(fixture.adminToken, 'DOMAIN_USER', ListRolesTypeEnum.Domain);

    expect(defaultRole.name).toEqual('DOMAIN_USER');
    expect(defaultRole.defaultRole).toBe(true);
  });
});
