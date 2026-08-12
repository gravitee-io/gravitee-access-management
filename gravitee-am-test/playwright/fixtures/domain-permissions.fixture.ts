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
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { addApplicationMembership, addDomainMembership, userMembership } from '@management-commands/membership-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import { ConsolePersona, createConsolePersona, signInToConsole, workerScope } from '../utils/permissions-helpers';
import { quietly, uniqueTestName } from '../utils/fixture-helpers';

/**
 * The world described by AM-6032: two independent domains with a different owner each, an
 * application owner one tier below, and a user holding nothing but the default organization role.
 * Between them they cover every rung of the ladder the Console reveals.
 */
export interface DomainPermissionsWorld {
  adminToken: string;
  /** Domain owned by `devManager`, containing both applications below. */
  devDomain: Domain;
  /** Separate domain owned by `qaManager` — nobody from the dev side has any membership on it. */
  qaDomain: Domain;
  /** Application in devDomain that `devAppOwner` owns. */
  devApplication: Application;
  /** Second application in devDomain that nobody below is a member of. */
  devOtherApplication: Application;
  devManager: ConsolePersona;
  qaManager: ConsolePersona;
  devAppOwner: ConsolePersona;
  /** Holds only the default ORGANIZATION_USER role — no domain anywhere. */
  basicUser: ConsolePersona;
}

export type DomainPermissionsFixtures = {
  domainWorld: DomainPermissionsWorld;
  signInAs: (persona: ConsolePersona) => Promise<void>;
};

const APP_SETTINGS = {
  settings: { oauth: { redirectUris: ['https://example.com/callback'], grantTypes: ['authorization_code'] } },
};

export const test = base.extend<DomainPermissionsFixtures>({
  domainWorld: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();

    const devDomain = await quietly(() => createDomain(adminToken, uniqueTestName(workerScope('pw-dev')), 'Playwright dev domain'));
    const qaDomain = await quietly(() => createDomain(adminToken, uniqueTestName(workerScope('pw-qa')), 'Playwright qa domain'));

    // The domains are deliberately never started: the Console reads from the management API and
    // nothing here touches the gateway, so starting them only adds sync latency to every test.
    const devApplication = await quietly(() =>
      createTestApp(uniqueTestName(workerScope('devapp')), devDomain, adminToken, 'web', APP_SETTINGS),
    );
    const devOtherApplication = await quietly(() =>
      createTestApp(uniqueTestName(workerScope('otherapp')), devDomain, adminToken, 'web', APP_SETTINGS),
    );

    const domainOwnerRole = await findOrganizationRoleByName(adminToken, 'DOMAIN_OWNER', ListRolesTypeEnum.Domain);
    const applicationOwnerRole = await findOrganizationRoleByName(adminToken, 'APPLICATION_OWNER', ListRolesTypeEnum.Application);

    const devManager = await createConsolePersona(adminToken, 'devmgr');
    const qaManager = await createConsolePersona(adminToken, 'qamgr');
    const devAppOwner = await createConsolePersona(adminToken, 'devappowner');
    const basicUser = await createConsolePersona(adminToken, 'basicuser');

    await quietly(() => addDomainMembership(devDomain.id, adminToken, userMembership(devManager.userId, domainOwnerRole.id)));
    await quietly(() => addDomainMembership(qaDomain.id, adminToken, userMembership(qaManager.userId, domainOwnerRole.id)));
    await quietly(() =>
      addApplicationMembership(devDomain.id, devApplication.id, adminToken, userMembership(devAppOwner.userId, applicationOwnerRole.id)),
    );

    await use({
      adminToken,
      devDomain,
      qaDomain,
      devApplication,
      devOtherApplication,
      devManager,
      qaManager,
      devAppOwner,
      basicUser,
    });

    for (const domain of [devDomain, qaDomain]) {
      await quietly(() => safeDeleteDomain(domain.id, adminToken));
    }
    for (const persona of [devManager, qaManager, devAppOwner, basicUser]) {
      await quietly(() => deleteOrganisationUser(adminToken, persona.userId)).catch(() => undefined);
    }
  },

  signInAs: async ({ page }, use) => {
    await use(async (persona: ConsolePersona) => signInToConsole(page, persona));
  },
});

export { expect } from '@playwright/test';
