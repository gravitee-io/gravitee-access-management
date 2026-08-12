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
import { createPersona, Persona, RbacFixture, setupRbacFixture } from './fixtures/rbac-fixture';
import {
  addApplicationMembership,
  addDomainMembership,
  addOrganizationMembership,
  userMembership,
} from '@management-commands/membership-management-commands';
import { createCustomOrganizationRole, deleteOrganizationRole } from '@management-commands/role-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import type { RoleEntity } from '@management-models/RoleEntity';

setup();

let fixture: RbacFixture;
let domainCreatorRole: RoleEntity;
let orgDomainReaderRole: RoleEntity;
let domainCreator: Persona;
let orgDomainReader: Persona;

const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });

beforeAll(async () => {
  fixture = await setupRbacFixture();

  // A DOMAIN-assignable role that nonetheless carries DOMAIN[CREATE]. Nothing validates that a
  // permission is relevant to the role's assignable type, so this role is accepted on save.
  domainCreatorRole = await createCustomOrganizationRole(fixture.adminToken, uniqueName('domain-creator', true), 'DOMAIN', [
    'domain_create',
    'domain_read',
  ]);
  // An ORGANIZATION-assignable role carrying the same domain read permission, for the contrast.
  orgDomainReaderRole = await createCustomOrganizationRole(fixture.adminToken, uniqueName('org-domain-reader', true), 'ORGANIZATION', [
    'domain_read',
    'domain_list',
  ]);

  domainCreator = await createPersona(fixture.adminToken, 'domaincreator');
  orgDomainReader = await createPersona(fixture.adminToken, 'orgdomainreader');

  await addDomainMembership(fixture.domain.id, fixture.adminToken, userMembership(domainCreator.userId, domainCreatorRole.id));
  await addOrganizationMembership(fixture.adminToken, userMembership(orgDomainReader.userId, orgDomainReaderRole.id));
});

afterAll(async () => {
  for (const persona of [domainCreator, orgDomainReader]) {
    if (persona) {
      await deleteOrganisationUser(fixture.adminToken, persona.userId).catch(() => undefined);
    }
  }
  for (const role of [domainCreatorRole, orgDomainReaderRole]) {
    if (role) {
      await deleteOrganizationRole(fixture.adminToken, role.id).catch(() => undefined);
    }
  }
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * A role declares the tier it may be assigned to via `assignableType`. Assignment is refused
 * outright when that does not match the tier of the membership, which is the first of two
 * independent gates confining a role to its scope.
 */
describe('Role scope confinement - a role can only be assigned at its own tier', () => {
  it('should accept an ORGANIZATION-assignable role at organization level', async () => {
    await addOrganizationMembership(fixture.adminToken, userMembership(fixture.assignee.userId, fixture.roles.organizationUser.id));
  });

  it('should accept a DOMAIN-assignable role at domain level', async () => {
    await addDomainMembership(fixture.domain.id, fixture.adminToken, userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id));
  });

  it('should accept an APPLICATION-assignable role at application level', async () => {
    await addApplicationMembership(
      fixture.domain.id,
      fixture.application.id,
      fixture.adminToken,
      userMembership(fixture.assignee.userId, fixture.roles.applicationOwner.id),
    );
  });

  it('should refuse an ORGANIZATION-assignable role at domain level', async () => {
    await expect(
      addDomainMembership(
        fixture.domain.id,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, fixture.roles.organizationUser.id),
      ),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse an ORGANIZATION-assignable role at application level', async () => {
    await expect(
      addApplicationMembership(
        fixture.domain.id,
        fixture.application.id,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, fixture.roles.organizationUser.id),
      ),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse a DOMAIN-assignable role at organization level', async () => {
    await expect(
      addOrganizationMembership(fixture.adminToken, userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id)),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse an APPLICATION-assignable role at domain level', async () => {
    await expect(
      addDomainMembership(
        fixture.domain.id,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, fixture.roles.applicationOwner.id),
      ),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });

  it('should refuse an APPLICATION-assignable role at organization level', async () => {
    await expect(
      addOrganizationMembership(fixture.adminToken, userMembership(fixture.assignee.userId, fixture.roles.applicationOwner.id)),
    ).rejects.toMatchObject({ response: { status: 400 }, message: expect.stringContaining('Invalid role') });
  });
});

/**
 * The second gate. Nothing prevents a DOMAIN-assignable role from carrying DOMAIN[CREATE], and the
 * assignment above is perfectly valid, so the role looks like it grants domain creation. It does
 * not: creating a domain is authorised against the environment and organization tiers only, and a
 * domain-level membership is never consulted for it. The permission is unreachable by construction.
 */
describe('Role scope confinement - a permission is only honoured on a tier that is consulted', () => {
  it('should let the domain-scoped role read the domain it is assigned to', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      `/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}`,
      headers(domainCreator.token),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.domain.id);
  });

  it('should refuse domain creation to a domain-scoped role holding DOMAIN[CREATE]', async () => {
    const response = await performPost(
      getOrganisationManagementUrl(),
      `/environments/${process.env.AM_DEF_ENV_ID}/domains`,
      {
        name: uniqueName('scope-confinement-should-never-exist', true),
        description: 'must never be created',
        dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
      },
      headers(domainCreator.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

/**
 * The mirror image: a permission granted at organization level does reach every domain beneath it,
 * because domain reads are authorised against the domain, environment and organization tiers.
 * Together with the case above this pins which direction authority actually flows.
 */
describe('Role scope confinement - an organization-level grant reaches domains beneath it', () => {
  it('should let an organization-scoped role read a domain it was never assigned to', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      `/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.otherDomain.id}`,
      headers(orgDomainReader.token),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.otherDomain.id);
  });
});
