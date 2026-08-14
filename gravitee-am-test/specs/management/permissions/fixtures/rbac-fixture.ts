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

import { expect } from '@jest/globals';
import { requestAccessToken, requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createOrganisationUser, deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import {
  addApplicationMembership,
  addDomainMembership,
  addOrganizationMembership,
  userMembership,
} from '@management-commands/membership-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
// Type-only: `@management-models` is not mapped in the jest moduleNameMapper (see
// membership-management-commands.ts), so model imports must stay erasable.
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { RoleEntity } from '@management-models/RoleEntity';
import { JWT_FORMAT } from '@specs-utils/jwt-format';

/**
 * Jest runs spec files in separate worker processes, and faker reseeds identically in each one, so
 * uniqueName() alone yields the *same* sequence per worker. Every spec in this folder shares this
 * fixture and therefore its prefixes, which made parallel runs collide on
 * "A domain [...] already exists". The process id disambiguates the workers.
 */
const workerScope = (prefix: string) => `${prefix}-${process.pid}`;

export const RBAC_TEST = {
  DOMAIN_PREFIX: 'rbac-primary',
  OTHER_DOMAIN_PREFIX: 'rbac-other',
  APP_NAME: 'rbac-app',
  OTHER_APP_NAME: 'rbac-other-app',
  USER_PASSWORD: 'RbacP@ssw0rd123!',
  REDIRECT_URI: 'https://example.com/callback',
} as const;

/** A console user plus a token acting as that user, so tests can call the API as them. */
export interface Persona {
  username: string;
  email: string;
  userId: string;
  token: string;
}

export interface RbacFixture {
  adminToken: string;
  /** Domain the personas below are scoped to. */
  domain: Domain;
  /** Second domain nobody below has a membership on — used to prove isolation. */
  otherDomain: Domain;
  application: Application;
  /** Second application in the same domain that `appOwner` has no membership on. */
  otherApplication: Application;
  roles: {
    organizationUser: RoleEntity;
    domainOwner: RoleEntity;
    /** The role the membership cascade provisions implicitly on a parent domain. */
    domainUser: RoleEntity;
    domainPrimaryOwner: RoleEntity;
    applicationOwner: RoleEntity;
  };
  /** Holds ORGANIZATION_USER only — the built-in role that grants almost nothing. */
  orgUser: Persona;
  /** DOMAIN_OWNER on `domain`. */
  domainOwner: Persona;
  /** APPLICATION_OWNER on `application`, one tier below `domainOwner`. */
  appOwner: Persona;
  /** Holds only the default ORGANIZATION_USER role; used as the target of assignment attempts. */
  assignee: Persona;
  cleanup: () => Promise<void>;
}

/**
 * Creates an organization user and authenticates as them.
 *
 * Every console user is an organization user; roles at domain or application level are layered on
 * afterwards as memberships. Creating a user already grants them the default ORGANIZATION_USER
 * role, so a fresh persona is never permission-less — it starts from that small read-only
 * allowance, which is the baseline the deny-by-default cases are measured against.
 */
export async function createPersona(adminToken: string, label: string): Promise<Persona> {
  const username = uniqueName(workerScope(label), true).toLowerCase();
  const email = `${username}@test.com`;
  const user = await createOrganisationUser(adminToken, {
    firstName: label,
    lastName: 'Persona',
    email,
    username,
    password: RBAC_TEST.USER_PASSWORD,
    preRegistration: false,
  });
  expect(user.id).toEqual(expect.any(String));

  const token = await requestAccessToken(username, RBAC_TEST.USER_PASSWORD);
  expect(token).toMatch(JWT_FORMAT);

  // `/auth/token` authenticates from the HTTP Basic header, not the JSON body. If the header were
  // ever wrong the endpoint would happily return a token for a *different* principal (in practice
  // admin), and every deny-case in this folder would then be asserting the wrong user's
  // permissions. Confirm the token really belongs to this persona before handing it out.
  expect(decodeUsername(token)).toEqual(username);

  return { username, email, userId: user.id, token };
}

/** Reads `preferred_username` out of a JWT payload without pulling in a verification library. */
function decodeUsername(token: string): string {
  const payload = token.split('.')[1];
  const decoded = Buffer.from(payload, 'base64url').toString('utf8');
  return JSON.parse(decoded).preferred_username;
}

export const setupRbacFixture = async (): Promise<RbacFixture> => {
  let adminToken: string | null = null;
  let domain: Domain | null = null;
  let otherDomain: Domain | null = null;
  const personas: Persona[] = [];

  try {
    adminToken = await requestAdminAccessToken();
    expect(adminToken).toMatch(JWT_FORMAT);

    // Management-only tests: the domain must exist and be started, but nothing here reaches the
    // gateway, so there is no reason to wait for OIDC routing to come up.
    domain = (await setupDomainForTest(uniqueName(workerScope(RBAC_TEST.DOMAIN_PREFIX), true), { accessToken: adminToken })).domain;
    otherDomain = (await setupDomainForTest(uniqueName(workerScope(RBAC_TEST.OTHER_DOMAIN_PREFIX), true), { accessToken: adminToken }))
      .domain;

    const appSettings = {
      settings: {
        oauth: {
          redirectUris: [RBAC_TEST.REDIRECT_URI],
          grantTypes: ['authorization_code'],
        },
      },
    };
    const application = await createTestApp(uniqueName(workerScope(RBAC_TEST.APP_NAME), true), domain, adminToken, 'web', appSettings);
    expect(application.id).toEqual(expect.any(String));

    const otherApplication = await createTestApp(
      uniqueName(workerScope(RBAC_TEST.OTHER_APP_NAME), true),
      domain,
      adminToken,
      'web',
      appSettings,
    );
    expect(otherApplication.id).toEqual(expect.any(String));

    const [organizationUser, domainOwner, domainUser, domainPrimaryOwner, applicationOwner] = await Promise.all([
      findOrganizationRoleByName(adminToken, 'ORGANIZATION_USER', ListRolesTypeEnum.Organization),
      findOrganizationRoleByName(adminToken, 'DOMAIN_OWNER', ListRolesTypeEnum.Domain),
      findOrganizationRoleByName(adminToken, 'DOMAIN_USER', ListRolesTypeEnum.Domain),
      findOrganizationRoleByName(adminToken, 'DOMAIN_PRIMARY_OWNER', ListRolesTypeEnum.Domain),
      findOrganizationRoleByName(adminToken, 'APPLICATION_OWNER', ListRolesTypeEnum.Application),
    ]);

    // Each persona is registered for cleanup the moment it resolves, not once all four have.
    // `Promise.all` would reject on the first failure and skip the registration entirely, leaking
    // the users that had already been created; `allSettled` additionally guarantees that none is
    // still in flight when the catch block below starts deleting.
    const trackPersona = async (token: string, label: string): Promise<Persona> => {
      const persona = await createPersona(token, label);
      personas.push(persona);
      return persona;
    };

    const created = await Promise.allSettled([
      trackPersona(adminToken, 'orguser'),
      trackPersona(adminToken, 'domainowner'),
      trackPersona(adminToken, 'appowner'),
      trackPersona(adminToken, 'assignee'),
    ]);
    const failed = created.find((result): result is PromiseRejectedResult => result.status === 'rejected');
    if (failed) {
      throw failed.reason;
    }
    const [orgUser, domainOwnerPersona, appOwner, assignee] = created.map((result) => (result as PromiseFulfilledResult<Persona>).value);

    // ORGANIZATION_USER is already granted at user creation; these calls are upserts that make the
    // starting point explicit, matching how the manual test cases describe the setup.
    await addOrganizationMembership(adminToken, userMembership(orgUser.userId, organizationUser.id));
    await addOrganizationMembership(adminToken, userMembership(domainOwnerPersona.userId, organizationUser.id));
    await addOrganizationMembership(adminToken, userMembership(appOwner.userId, organizationUser.id));

    await addDomainMembership(domain.id, adminToken, userMembership(domainOwnerPersona.userId, domainOwner.id));
    await addApplicationMembership(domain.id, application.id, adminToken, userMembership(appOwner.userId, applicationOwner.id));

    const cleanup = async () => {
      for (const id of [domain?.id, otherDomain?.id]) {
        if (id) {
          await safeDeleteDomain(id, adminToken);
        }
      }
      // Organization users are not domain-scoped, so domain deletion does not cascade to them.
      for (const persona of personas) {
        try {
          await deleteOrganisationUser(adminToken, persona.userId);
        } catch {
          // best effort — a failed persona delete must not mask the test result
        }
      }
    };

    return {
      adminToken,
      domain,
      otherDomain,
      application,
      otherApplication,
      roles: { organizationUser, domainOwner, domainUser, domainPrimaryOwner, applicationOwner },
      orgUser,
      domainOwner: domainOwnerPersona,
      appOwner,
      assignee,
      cleanup,
    };
  } catch (error) {
    for (const id of [domain?.id, otherDomain?.id]) {
      if (id && adminToken) {
        try {
          await safeDeleteDomain(id, adminToken);
        } catch (cleanupError) {
          console.error('Failed to clean up domain after setup failure:', cleanupError);
        }
      }
    }
    for (const persona of personas) {
      if (adminToken) {
        try {
          await deleteOrganisationUser(adminToken, persona.userId);
        } catch {
          // best effort
        }
      }
    }
    throw error;
  }
};
