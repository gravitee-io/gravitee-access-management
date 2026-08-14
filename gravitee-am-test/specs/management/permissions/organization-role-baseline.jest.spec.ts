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
  addDomainMembership,
  addOrganizationMembership,
  groupMembership,
  listOrganizationMemberships,
  removeOrganizationMembership,
  userMembership,
} from '@management-commands/membership-management-commands';
import { createCustomOrganizationRole, deleteOrganizationRole } from '@management-commands/role-management-commands';
import {
  addOrganizationGroupMember,
  createOrganizationGroup,
  deleteOrganizationGroup,
} from '@management-commands/group-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';

setup(200000);
// These cases run as one ordered narrative, so a retry must not resume after a later step has
// already mutated the state an earlier one asserts against (GUIDELINES §3).
retryImmediatelyForThisFile();

let fixture: RbacFixture;
const createdUsers: Persona[] = [];
const createdGroupIds: string[] = [];

const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });
const domainPath = (domainId: string) => `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}`;

const newPersona = async (label: string) => {
  const persona = await createPersona(fixture.adminToken, label);
  createdUsers.push(persona);
  return persona;
};

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  for (const groupId of createdGroupIds) {
    await deleteOrganizationGroup(fixture.adminToken, groupId).catch(() => undefined);
  }
  for (const persona of createdUsers) {
    await deleteOrganisationUser(fixture.adminToken, persona.userId).catch(() => undefined);
  }
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * "Users must have at least one ORGANIZATION role to access the AM Console."
 *
 * Console access is not a property of authentication — a user stripped of every organization role
 * still signs in and still receives a token. What they lose is the ability to resolve anything, so
 * the distinction only shows up on a request, which is what makes it worth pinning.
 */
describe('Organization role baseline - an organization role is what makes the Console usable', () => {
  it('should let a user holding the default organization role list environments', async () => {
    const persona = await newPersona('orgbaseline');

    const response = await performGet(getOrganisationManagementUrl(), '/environments', headers(persona.token));

    expect(response.status).toBe(200);
  });

  it('should refuse the same request once every organization role is removed', async () => {
    const persona = await newPersona('strippedbaseline');

    const { memberships } = await listOrganizationMemberships(fixture.adminToken);
    const theirs = memberships.filter((membership) => membership.memberId === persona.userId);
    expect(theirs).toHaveLength(1);

    await removeOrganizationMembership(fixture.adminToken, theirs[0].id);

    const response = await performGet(getOrganisationManagementUrl(), '/environments', headers(persona.token));

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

/**
 * "Direct user permissions and group permissions merge when applied to the same user."
 *
 * Group memberships are resolved by a separate branch of the permission lookup to the one every
 * other test in this folder exercises, so nothing else here would notice if it broke.
 */
describe('Organization role baseline - a role assigned to a group reaches its members', () => {
  let groupMember: Persona;

  beforeAll(async () => {
    groupMember = await newPersona('groupmember');
  });

  it('should refuse the domain before the group is given any role', async () => {
    const response = await performGet(getOrganisationManagementUrl(), domainPath(fixture.domain.id), headers(groupMember.token));

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should grant the domain through group membership alone', async () => {
    const group = await createOrganizationGroup(fixture.adminToken, uniqueName('perm-group', true));
    createdGroupIds.push(group.id);
    await addOrganizationGroupMember(fixture.adminToken, group.id, groupMember.userId);

    // The role is assigned to the group, never to the user.
    await addDomainMembership(fixture.domain.id, fixture.adminToken, groupMembership(group.id, fixture.roles.domainOwner.id));

    const response = await performGet(getOrganisationManagementUrl(), domainPath(fixture.domain.id), headers(groupMember.token));

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.domain.id);
  });

  it('should still confine a group-derived role to the domain it was assigned to', async () => {
    // Anchored within the test rather than relying on the previous one: a caller who never
    // received the role at all is refused here too, so the 403 alone cannot tell confinement
    // apart from the group membership silently not applying.
    const granted = await performGet(getOrganisationManagementUrl(), domainPath(fixture.domain.id), headers(groupMember.token));
    expect(granted.status).toBe(200);

    const response = await performGet(getOrganisationManagementUrl(), domainPath(fixture.otherDomain.id), headers(groupMember.token));

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

/**
 * "DOMAIN READ allows reading a specific domain when assigned to that domain, but reading all
 * domains when assigned to an organization." The same permission, two meanings, chosen by the tier
 * it is granted at — so an organization-level grant must reach every domain, not merely one.
 */
describe('Organization role baseline - an organization-level grant spans every domain', () => {
  it('should let a user assigned the role at organization level read both domains', async () => {
    const orgReader = await newPersona('orgspanreader');

    const { memberships } = await listOrganizationMemberships(fixture.adminToken);
    const theirs = memberships.filter((membership) => membership.memberId === orgReader.userId);
    expect(theirs).toHaveLength(1);
    await removeOrganizationMembership(fixture.adminToken, theirs[0].id);

    const spanningRole = await createCustomOrganizationRole(fixture.adminToken, uniqueName('org-span-reader', true), 'ORGANIZATION', [
      'domain_read',
      'domain_list',
    ]);
    await addOrganizationMembership(fixture.adminToken, userMembership(orgReader.userId, spanningRole.id));

    for (const domainId of [fixture.domain.id, fixture.otherDomain.id]) {
      const response = await performGet(getOrganisationManagementUrl(), domainPath(domainId), headers(orgReader.token));
      expect(response.status).toBe(200);
      expect(response.body.id).toEqual(domainId);
    }

    await deleteOrganizationRole(fixture.adminToken, spanningRole.id).catch(() => undefined);
  });
});
