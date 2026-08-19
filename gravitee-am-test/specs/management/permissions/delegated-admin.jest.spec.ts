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
import { RbacFixture, setupRbacFixture } from './fixtures/rbac-fixture';
import {
  addApplicationMembership,
  addDomainMembership,
  listApplicationMemberships,
  listDomainMemberships,
  userMembership,
} from '@management-commands/membership-management-commands';
import { getCurrentUser } from '@management-commands/organisation-user-commands';

setup(200000);

let fixture: RbacFixture;

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * Managing memberships is gated by its own permissions (DOMAIN_MEMBER, APPLICATION_MEMBER) that
 * are separate from the permissions to manage the resource itself. Delegated administration is
 * therefore the place where privilege escalation would surface: an owner who can grant roles can
 * grant roles they do not themselves hold, or grant them somewhere they have no authority.
 */
describe('Delegated administration - owners can manage members within their own scope', () => {
  it('should allow a DOMAIN_OWNER to add a member to their own domain', async () => {
    await addDomainMembership(
      fixture.domain.id,
      fixture.domainOwner.token,
      userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id),
    );

    const { memberships } = await listDomainMemberships(fixture.domain.id, fixture.adminToken);
    expect(memberships.map((m) => m.memberId)).toContain(fixture.assignee.userId);
  });

  it('should allow an APPLICATION_OWNER to add a member to their own application', async () => {
    await addApplicationMembership(
      fixture.domain.id,
      fixture.application.id,
      fixture.appOwner.token,
      userMembership(fixture.assignee.userId, fixture.roles.applicationOwner.id),
    );

    const { memberships } = await listApplicationMemberships(fixture.domain.id, fixture.application.id, fixture.adminToken);
    expect(memberships.map((m) => m.memberId)).toContain(fixture.assignee.userId);
  });
});

describe('Delegated administration - membership management is refused outside that scope', () => {
  // A 403 on its own is weak evidence: an unauthenticated or malformed request fails the same way,
  // so these would stay green even if the permission check were removed entirely. Each case below
  // therefore asserts the denial message, and the bare organization user is separately shown to
  // hold a working token so its refusal cannot be vacuous.
  it('should authenticate a bare ORGANIZATION_USER successfully despite holding no permissions', async () => {
    const currentUser = await getCurrentUser(fixture.orgUser.token);
    expect(currentUser.email).toEqual(fixture.orgUser.email);
  });

  it('should refuse a bare ORGANIZATION_USER adding a member to a domain', async () => {
    await expect(
      addDomainMembership(fixture.domain.id, fixture.orgUser.token, userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id)),
    ).rejects.toMatchObject({ response: { status: 403 }, message: expect.stringContaining('Permission denied') });
  });

  it('should refuse an APPLICATION_OWNER adding a member to the parent domain', async () => {
    await expect(
      addDomainMembership(fixture.domain.id, fixture.appOwner.token, userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id)),
    ).rejects.toMatchObject({ response: { status: 403 }, message: expect.stringContaining('Permission denied') });
  });

  it('should refuse a DOMAIN_OWNER adding a member to a domain they have no membership on', async () => {
    await expect(
      addDomainMembership(
        fixture.otherDomain.id,
        fixture.domainOwner.token,
        userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id),
      ),
    ).rejects.toMatchObject({ response: { status: 403 }, message: expect.stringContaining('Permission denied') });
  });
});

describe('Delegated administration - escalation guards', () => {
  // Both guards below answer with 400, as does ordinary payload validation, so each asserts the
  // specific message — otherwise an unrelated validation failure would keep these green.
  it('should refuse granting a second DOMAIN_PRIMARY_OWNER on the same domain', async () => {
    await expect(
      addDomainMembership(
        fixture.domain.id,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, fixture.roles.domainPrimaryOwner.id),
      ),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('DOMAIN can only have one PRIMARY_OWNER'),
    });
  });

  it('should refuse a domain-assignable role used for an application membership', async () => {
    await expect(
      addApplicationMembership(
        fixture.domain.id,
        fixture.application.id,
        fixture.adminToken,
        userMembership(fixture.assignee.userId, fixture.roles.domainOwner.id),
      ),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Invalid role'),
    });
  });
});
