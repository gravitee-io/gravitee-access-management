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
import {
  createCustomOrganizationRole,
  deleteOrganizationRole,
  findOrganizationRoleByName,
} from '@management-commands/role-management-commands';
import { addApplicationMembership, userMembership } from '@management-commands/membership-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import { ConsolePersona, createConsolePersona, signInToConsole, workerScope } from '../utils/permissions-helpers';
import { quietly, uniqueTestName } from '../utils/fixture-helpers';

/**
 * The world described by AM-6036: one domain holding two applications, and two organization users
 * who differ only in the role they hold on the *first* application — one owns it, the other holds
 * a custom read-only role. Everything the Console shows them should follow from that difference.
 */
export interface PermissionsWorld {
  adminToken: string;
  domain: Domain;
  /** The application both personas are members of. */
  ownedApplication: Application;
  /** Second application in the same domain, which neither persona is a member of. */
  otherApplication: Application;
  /** APPLICATION_OWNER on `ownedApplication`. */
  appOwner: ConsolePersona;
  /** Custom APPLICATION-assignable role with only APPLICATION[READ] on `ownedApplication`. */
  appViewer: ConsolePersona;
}

export type PermissionsFixtures = {
  permissionsWorld: PermissionsWorld;
  /** Signs in through the Console login form as the given persona. */
  signInAs: (persona: ConsolePersona) => Promise<void>;
};

export const test = base.extend<PermissionsFixtures>({
  permissionsWorld: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();

    const domain = await quietly(() => createDomain(adminToken, uniqueTestName(workerScope('pw-perm')), 'Playwright permissions domain'));

    // The domain is deliberately never started: the Console reads from the management API and
    // nothing here touches the gateway, so starting it only adds sync latency to every test.
    const appSettings = {
      settings: { oauth: { redirectUris: ['https://example.com/callback'], grantTypes: ['authorization_code'] } },
    };
    const ownedApplication = await quietly(() =>
      createTestApp(uniqueTestName(workerScope('mywebapp')), domain, adminToken, 'web', appSettings),
    );
    const otherApplication = await quietly(() =>
      createTestApp(uniqueTestName(workerScope('otherwebapp')), domain, adminToken, 'web', appSettings),
    );

    const applicationOwnerRole = await findOrganizationRoleByName(adminToken, 'APPLICATION_OWNER', ListRolesTypeEnum.Application);
    const applicationViewerRole = await createCustomOrganizationRole(
      adminToken,
      uniqueTestName(workerScope('Application Viewer')),
      'APPLICATION',
      ['application_read'],
    );

    const appOwner = await createConsolePersona(adminToken, 'devappowner');
    const appViewer = await createConsolePersona(adminToken, 'appviewer');

    await quietly(() =>
      addApplicationMembership(domain.id, ownedApplication.id, adminToken, userMembership(appOwner.userId, applicationOwnerRole.id)),
    );
    await quietly(() =>
      addApplicationMembership(domain.id, ownedApplication.id, adminToken, userMembership(appViewer.userId, applicationViewerRole.id)),
    );

    await use({ adminToken, domain, ownedApplication, otherApplication, appOwner, appViewer });

    // Domain deletion cascades applications and their memberships; organization users and the
    // custom role live outside the domain and must be removed explicitly.
    await quietly(() => safeDeleteDomain(domain.id, adminToken));
    for (const persona of [appOwner, appViewer]) {
      await quietly(() => deleteOrganisationUser(adminToken, persona.userId)).catch(() => undefined);
    }
    await quietly(() => deleteOrganizationRole(adminToken, applicationViewerRole.id)).catch(() => undefined);
  },

  signInAs: async ({ page }, use) => {
    await use(async (persona: ConsolePersona) => signInToConsole(page, persona));
  },
});

export { expect } from '@playwright/test';
