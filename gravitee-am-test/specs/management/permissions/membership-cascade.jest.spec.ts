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
  getEnvironmentMemberPermissions,
  listDomainMemberships,
  userMembership,
} from '@management-commands/membership-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';

setup(200000);
// These cases run as one ordered narrative, so a retry must not resume after a later step has
// already mutated the state an earlier one asserts against (GUIDELINES §3).
retryImmediatelyForThisFile();

let fixture: RbacFixture;
const created: Persona[] = [];

/** ENVIRONMENT_USER, the role the cascade grants, carries exactly these. */
const ENVIRONMENT_USER_PERMISSIONS = ['environment_read', 'domain_list', 'data_plane_list', 'data_plane_read'];

const newPersona = async (label: string): Promise<Persona> => {
  const persona = await createPersona(fixture.adminToken, label);
  created.push(persona);
  return persona;
};

const domainMembershipOf = async (userId: string) => {
  const { memberships } = await listDomainMemberships(fixture.domain.id, fixture.adminToken);
  return memberships.filter((membership) => membership.memberId === userId);
};

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  for (const persona of created) {
    await deleteOrganisationUser(fixture.adminToken, persona.userId).catch(() => undefined);
  }
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * Adding a member at one tier silently creates memberships on the tiers above it: an application
 * assignment provisions DOMAIN_USER on the parent domain and ENVIRONMENT_USER on the environment,
 * and a domain assignment provisions ENVIRONMENT_USER. This is what makes an assigned user able to
 * see the domain at all, so it has failure modes in both directions — under-provision and the user
 * sees nothing, over-provision and they gain visibility they were never granted.
 *
 * Environment memberships cannot be listed through the API, so they are observed through the
 * effective permissions the assigned user resolves to at the environment tier.
 */
describe('Membership cascade - assigning at application level provisions the tiers above', () => {
  let appMember: Persona;

  beforeAll(async () => {
    appMember = await newPersona('cascadeapp');
  });

  it('should start with no domain membership and no environment access at all', async () => {
    expect(await domainMembershipOf(appMember.userId)).toHaveLength(0);
    // The endpoint is itself gated by ENVIRONMENT[READ], so an unprovisioned user cannot even
    // reach it — a stronger baseline than resolving to an empty permission set.
    await expect(getEnvironmentMemberPermissions(appMember.token)).rejects.toMatchObject({
      response: { status: 403 },
      message: expect.stringContaining('Permission denied'),
    });
  });

  it('should provision a DOMAIN_USER membership on the parent domain', async () => {
    await addApplicationMembership(
      fixture.domain.id,
      fixture.application.id,
      fixture.adminToken,
      userMembership(appMember.userId, fixture.roles.applicationOwner.id),
    );

    const memberships = await domainMembershipOf(appMember.userId);
    expect(memberships).toHaveLength(1);
    expect(memberships[0].roleId).toEqual(fixture.roles.domainUser.id);
  });

  it('should provision environment permissions for the assigned user', async () => {
    const permissions = await getEnvironmentMemberPermissions(appMember.token);
    expect(permissions.sort()).toEqual([...ENVIRONMENT_USER_PERMISSIONS].sort());
  });
});

describe('Membership cascade - assigning at domain level provisions the environment only', () => {
  let domainMember: Persona;

  beforeAll(async () => {
    domainMember = await newPersona('cascadedomain');
  });

  it('should start with no environment access at all', async () => {
    await expect(getEnvironmentMemberPermissions(domainMember.token)).rejects.toMatchObject({
      response: { status: 403 },
      message: expect.stringContaining('Permission denied'),
    });
  });

  it('should keep the explicitly assigned domain role rather than substituting DOMAIN_USER', async () => {
    await addDomainMembership(fixture.domain.id, fixture.adminToken, userMembership(domainMember.userId, fixture.roles.domainOwner.id));

    const memberships = await domainMembershipOf(domainMember.userId);
    expect(memberships).toHaveLength(1);
    expect(memberships[0].roleId).toEqual(fixture.roles.domainOwner.id);
  });

  it('should provision environment permissions for the assigned user', async () => {
    const permissions = await getEnvironmentMemberPermissions(domainMember.token);
    expect(permissions.sort()).toEqual([...ENVIRONMENT_USER_PERMISSIONS].sort());
  });
});

/**
 * The cascade provisions only where nothing exists yet. A member who already holds a domain role
 * and is later added to an application inside that domain must keep the role they were given — a
 * cascade that overwrote it would silently demote a domain owner to DOMAIN_USER.
 */
describe('Membership cascade - an existing membership is never downgraded', () => {
  let existingOwner: Persona;

  beforeAll(async () => {
    existingOwner = await newPersona('cascadeowner');
    await addDomainMembership(fixture.domain.id, fixture.adminToken, userMembership(existingOwner.userId, fixture.roles.domainOwner.id));
  });

  it('should leave the domain role untouched when the member is added to an application', async () => {
    await addApplicationMembership(
      fixture.domain.id,
      fixture.application.id,
      fixture.adminToken,
      userMembership(existingOwner.userId, fixture.roles.applicationOwner.id),
    );

    const memberships = await domainMembershipOf(existingOwner.userId);
    expect(memberships).toHaveLength(1);
    expect(memberships[0].roleId).toEqual(fixture.roles.domainOwner.id);
  });
});
