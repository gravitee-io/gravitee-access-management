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
  listDomainMemberships,
  listOrganizationMemberships,
  removeDomainMembership,
  userMembership,
} from '@management-commands/membership-management-commands';
import { createCustomOrganizationRole, deleteOrganizationRole } from '@management-commands/role-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { performGet, performPatch } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import type { RoleEntity } from '@management-models/RoleEntity';

setup();
// The steps below are one continuous narrative and depend on each other, so a mid-file retry must
// not restart from a later step (GUIDELINES §3).
retryImmediatelyForThisFile();

let fixture: RbacFixture;
let newAdmin: Persona;
let readOnlyRole: RoleEntity;

const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });
const domainPath = () => `/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}`;

const readDomainAs = (token: string) => performGet(getOrganisationManagementUrl(), domainPath(), headers(token));

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (newAdmin) {
    await deleteOrganisationUser(fixture.adminToken, newAdmin.userId).catch(() => undefined);
  }
  if (readOnlyRole) {
    await deleteOrganizationRole(fixture.adminToken, readOnlyRole.id).catch(() => undefined);
  }
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * The administrator's actual workflow, start to finish: take on a new colleague, build a role that
 * grants precisely what they need, hand it to them, and later take it away again. Each step is
 * covered somewhere in this folder in isolation; running them as one story is what proves they
 * compose — in particular that granting is reversible, which nothing else here checks.
 */
describe('Role assignment journey - a new administrator is onboarded and later revoked', () => {
  it('should give a newly created user the default organization role without being asked', async () => {
    newAdmin = await createPersona(fixture.adminToken, 'journeyadmin');

    const { memberships } = await listOrganizationMemberships(fixture.adminToken);
    const theirs = memberships.filter((membership) => membership.memberId === newAdmin.userId);

    expect(theirs).toHaveLength(1);
    expect(theirs[0].roleId).toEqual(fixture.roles.organizationUser.id);
  });

  it('should not let that default role reach any domain', async () => {
    const response = await readDomainAs(newAdmin.token);

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should create a custom role granting domain read but deliberately not domain update', async () => {
    readOnlyRole = await createCustomOrganizationRole(fixture.adminToken, uniqueName('journey-domain-reader', true), 'DOMAIN', [
      'domain_read',
    ]);

    expect(readOnlyRole.permissions).toEqual(['domain_read']);
  });

  it('should grant domain access once the role is assigned', async () => {
    await addDomainMembership(fixture.domain.id, fixture.adminToken, userMembership(newAdmin.userId, readOnlyRole.id));

    const response = await readDomainAs(newAdmin.token);

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.domain.id);
  });

  it('should still refuse the action whose verb the role withholds', async () => {
    // The role carries READ on exactly this resource but not UPDATE, so the two must diverge —
    // holding a permission is not the same as holding every action on it.
    const response = await performPatch(
      getOrganisationManagementUrl(),
      domainPath(),
      { description: 'changed by a read-only role' },
      headers(newAdmin.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should take the access away again when the membership is removed', async () => {
    const { memberships } = await listDomainMemberships(fixture.domain.id, fixture.adminToken);
    const theirs = memberships.find((membership) => membership.memberId === newAdmin.userId);
    expect(theirs.roleId).toEqual(readOnlyRole.id);

    await removeDomainMembership(fixture.domain.id, fixture.adminToken, theirs.id);

    const response = await readDomainAs(newAdmin.token);
    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should leave the user in place with only their default organization role', async () => {
    const { memberships } = await listOrganizationMemberships(fixture.adminToken);
    const theirs = memberships.filter((membership) => membership.memberId === newAdmin.userId);

    expect(theirs).toHaveLength(1);
    expect(theirs[0].roleId).toEqual(fixture.roles.organizationUser.id);
  });
});
