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

import { test as base } from '@playwright/test';
import crossFetch from 'cross-fetch';
// Native Node fetch makes the generated SDK drop fields silently — see GUIDELINES §10.
globalThis.fetch = crossFetch;

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { createCustomOrganizationRole, deleteOrganizationRole } from '@management-commands/role-management-commands';
import { addOrganizationMembership, userMembership } from '@management-commands/membership-management-commands';
import type { Domain } from '@management-models/Domain';
import type { RoleEntity } from '@management-models/RoleEntity';
import { ConsolePersona, createConsolePersona, signInToConsole, workerScope } from '../utils/permissions-helpers';
import { quietly, uniqueTestName } from '../utils/fixture-helpers';

/**
 * The world described by AM-6031, seen from the organization tier: a domain that exists, a user
 * holding nothing but the default organization role, and an auditor carrying a custom
 * organization-level role that grants reading a domain but not listing or creating one.
 */
export interface OrgPermissionsWorld {
  adminToken: string;
  /** A domain that exists so that "cannot see it" is a real absence rather than an empty instance. */
  domain: Domain;
  /** Holds only the default ORGANIZATION_USER role. */
  orgUser: ConsolePersona;
  /**
   * Holds *only* a custom organization role granting DOMAIN[READ]. Assigning a role at a reference
   * replaces the membership there rather than adding a second one, so this also removes the default
   * ORGANIZATION_USER — which is precisely the "user with only a custom ORGANIZATION role" the
   * manual case describes.
   */
  auditor: ConsolePersona;
  auditorRole: RoleEntity;
}

export type OrgPermissionsFixtures = {
  orgWorld: OrgPermissionsWorld;
  signInAs: (persona: ConsolePersona) => Promise<void>;
};

export const test = base.extend<OrgPermissionsFixtures>({
  orgWorld: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();

    // Never started: the Console reads from the management API and nothing here reaches the gateway.
    const domain = await quietly(() => createDomain(adminToken, uniqueTestName(workerScope('pw-org')), 'Playwright org domain'));

    // DOMAIN[READ] without DOMAIN[LIST] is the interesting shape: the holder could read this domain
    // by id, yet it must not appear in the list, and they must not be offered a way to create one.
    const auditorRole = await createCustomOrganizationRole(adminToken, uniqueTestName(workerScope('Org-QA-Auditor')), 'ORGANIZATION', [
      'domain_read',
    ]);

    const orgUser = await createConsolePersona(adminToken, 'orgbasic');
    const auditor = await createConsolePersona(adminToken, 'auditor');

    // Replaces the default ORGANIZATION_USER granted at creation: a member holds one role per
    // reference, so this leaves the auditor with DOMAIN[READ] and nothing else.
    await quietly(() => addOrganizationMembership(adminToken, userMembership(auditor.userId, auditorRole.id)));

    await use({ adminToken, domain, orgUser, auditor, auditorRole });

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
    for (const persona of [orgUser, auditor]) {
      await quietly(() => deleteOrganisationUser(adminToken, persona.userId)).catch(() => undefined);
    }
    await quietly(() => deleteOrganizationRole(adminToken, auditorRole.id)).catch(() => undefined);
  },

  signInAs: async ({ page }, use) => {
    await use(async (persona: ConsolePersona) => signInToConsole(page, persona));
  },
});

export { expect } from '@playwright/test';
