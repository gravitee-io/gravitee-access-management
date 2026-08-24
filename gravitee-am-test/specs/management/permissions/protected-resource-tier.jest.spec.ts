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
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import { createPersona, Persona, RbacFixture, setupRbacFixture } from './fixtures/rbac-fixture';
import {
  addApplicationMembership,
  addDomainMembership,
  listDomainMemberships,
  removeDomainMembership,
  addOrganizationMembership,
  addProtectedResourceMembership,
  listProtectedResourceMemberships,
  removeProtectedResourceMembership,
  userMembership,
} from '@management-commands/membership-management-commands';
import { createProtectedResource } from '@management-commands/protected-resources-management-commands';
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { performDelete, performGet } from '@gateway-commands/oauth-oidc-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import { uniqueName } from '@utils-commands/misc';
import type { RoleEntity } from '@management-models/RoleEntity';

setup(200000);
// These cases run as one ordered narrative, so a retry must not resume after a later step has
// already mutated the state an earlier one asserts against (GUIDELINES §3).
retryImmediatelyForThisFile();

let fixture: RbacFixture;
let member: Persona;
let protectedResourceId: string;
let resourceUserRole: RoleEntity;
let resourcePrimaryOwnerRole: RoleEntity;

const managementUrl = () => `${process.env.AM_MANAGEMENT_URL}/management`;
const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });

const listResourcesAs = (token: string) =>
  performGet(
    managementUrl(),
    `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}` +
      `/protected-resources?type=MCP_SERVER`,
    headers(token),
  );

const readResourceAs = (token: string) =>
  performGet(
    managementUrl(),
    `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}` +
      `/protected-resources/${protectedResourceId}`,
    headers(token),
  );

const deleteResourceAs = (token: string) =>
  performDelete(
    managementUrl(),
    `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}` +
      `/protected-resources/${protectedResourceId}`,
    headers(token),
  );

beforeAll(async () => {
  fixture = await setupRbacFixture();

  const created = await createProtectedResource(fixture.domain.id, fixture.adminToken, {
    name: uniqueName('rbac-mcp', true),
    type: 'MCP_SERVER',
    resourceIdentifiers: [`https://mcp.test/${uniqueName('rbac', true)}`],
  });
  protectedResourceId = created.id;

  [resourceUserRole, resourcePrimaryOwnerRole] = await Promise.all([
    findOrganizationRoleByName(fixture.adminToken, 'PROTECTED_RESOURCE_USER', ListRolesTypeEnum.ProtectedResource),
    findOrganizationRoleByName(fixture.adminToken, 'PROTECTED_RESOURCE_PRIMARY_OWNER', ListRolesTypeEnum.ProtectedResource),
  ]);

  member = await createPersona(fixture.adminToken, 'resourcemember');
});

afterAll(async () => {
  if (member) {
    await deleteOrganisationUser(fixture.adminToken, member.userId).catch(() => undefined);
  }
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * Protected resources are the fifth assignable tier and the newest, and until now the only one
 * with no permission coverage at all. The mechanics are meant to be the same as the tiers above,
 * which is exactly why they are worth asserting rather than assumed: the tier is wired into the
 * same resolution path, and a gap in that wiring would be silent.
 */
describe('Protected resource tier - a role is confined to the tier it is assignable to', () => {
  it('should accept a protected-resource role on a protected resource', async () => {
    await addProtectedResourceMembership(
      fixture.domain.id,
      protectedResourceId,
      fixture.adminToken,
      userMembership(member.userId, resourceUserRole.id),
    );

    const { memberships } = await listProtectedResourceMemberships(fixture.domain.id, protectedResourceId, fixture.adminToken);
    expect(memberships.map((membership) => membership.memberId)).toContain(member.userId);
  });

  it('should refuse a protected-resource role at domain level', async () => {
    await expect(
      addDomainMembership(fixture.domain.id, fixture.adminToken, userMembership(fixture.assignee.userId, resourceUserRole.id)),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse a protected-resource role at application level', async () => {
    await expect(
      addApplicationMembership(
        fixture.domain.id,
        fixture.application.id,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, resourceUserRole.id),
      ),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse a protected-resource role at organization level', async () => {
    await expect(
      addOrganizationMembership(fixture.adminToken, userMembership(fixture.assignee.userId, resourceUserRole.id)),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse a domain role on a protected resource', async () => {
    // The mirror of the cases above: the tier rejects roles belonging to another tier just as the
    // other tiers reject its own.
    await expect(
      addProtectedResourceMembership(
        fixture.domain.id,
        protectedResourceId,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id),
      ),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse a second primary owner on the same protected resource', async () => {
    // Creating the resource already made its creator primary owner, so this is the tier's own
    // instance of the single-primary-owner rule.
    await expect(
      addProtectedResourceMembership(
        fixture.domain.id,
        protectedResourceId,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, resourcePrimaryOwnerRole.id),
      ),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('can only have one PRIMARY_OWNER'),
    });
  });
});

describe('Protected resource tier - membership grants and revokes access', () => {
  it('should refuse the resource list to someone with no membership on it', async () => {
    const outsider = await createPersona(fixture.adminToken, 'resourceoutsider');

    try {
      const response = await listResourcesAs(outsider.token);

      expect(response.status).toBe(403);
      expect(response.body.message).toEqual('Permission denied');
    } finally {
      await deleteOrganisationUser(fixture.adminToken, outsider.userId).catch(() => undefined);
    }
  });

  it('should refuse a delete to someone with no membership on it', async () => {
    // Delete takes no type either. The refusal must come from the permission check rather than the
    // missing parameter, so an outsider learns nothing about the resource.
    const outsider = await createPersona(fixture.adminToken, 'resourcedeleter');

    try {
      const response = await deleteResourceAs(outsider.token);

      expect(response.status).toBe(403);
      expect(response.body.message).toEqual('Permission denied');
    } finally {
      await deleteOrganisationUser(fixture.adminToken, outsider.userId).catch(() => undefined);
    }
  });

  it('should show the resource to the member assigned to it', async () => {
    const response = await listResourcesAs(member.token);

    expect(response.status).toBe(200);
    expect(response.body.data.map((resource) => resource.id)).toContain(protectedResourceId);

    // Read-by-id takes no type: the id already identifies the resource.
    const read = await readResourceAs(member.token);
    expect(read.status).toBe(200);
    expect(read.body).toMatchObject({ id: protectedResourceId, type: 'mcp_server' });
  });

  it('should remove them from the resource when the membership is deleted', async () => {
    const { memberships } = await listProtectedResourceMemberships(fixture.domain.id, protectedResourceId, fixture.adminToken);
    const theirs = memberships.filter((membership) => membership.memberId === member.userId);
    expect(theirs).toHaveLength(1);

    await removeProtectedResourceMembership(fixture.domain.id, protectedResourceId, fixture.adminToken, theirs[0].id);

    const after = await listProtectedResourceMemberships(fixture.domain.id, protectedResourceId, fixture.adminToken);
    expect(after.memberships.map((membership) => membership.memberId)).not.toContain(member.userId);
  });

  it('should leave the domain access the assignment implicitly granted', async () => {
    // Assigning at resource level provisioned DOMAIN_USER on the parent domain, and deleting the
    // resource membership does not take it back. DOMAIN_USER carries protected_resource_list, so
    // the resource is still listed — the caller has lost the resource, not the domain.
    const { memberships } = await listDomainMemberships(fixture.domain.id, fixture.adminToken);
    const domainMembership = memberships.filter((membership) => membership.memberId === member.userId);
    expect(domainMembership).toHaveLength(1);

    expect(domainMembership[0].roleId).toEqual(fixture.roles.domainUser.id);
    expect((await listResourcesAs(member.token)).status).toBe(200);
  });

  it('should refuse the list once that domain membership is removed too', async () => {
    // Only when both are gone is the access actually revoked, which is the part an administrator
    // revoking someone from a resource would most easily miss.
    const { memberships } = await listDomainMemberships(fixture.domain.id, fixture.adminToken);
    const domainMembership = memberships.filter((membership) => membership.memberId === member.userId);
    expect(domainMembership).toHaveLength(1);

    await removeDomainMembership(fixture.domain.id, fixture.adminToken, domainMembership[0].id);

    const response = await listResourcesAs(member.token);
    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});
