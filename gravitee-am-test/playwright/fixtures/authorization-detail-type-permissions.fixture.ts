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
  getOrganizationRole,
} from '@management-commands/role-management-commands';
import { addDomainMembership, userMembership } from '@management-commands/membership-management-commands';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import { NewRoleAssignableTypeEnum } from '@management-models/NewRole';
import type { AuthorizationDetailType } from '@management-models/AuthorizationDetailType';
import type { Domain } from '@management-models/Domain';
import { ConsolePersona, createConsolePersona, signInToConsole, workerScope } from '../utils/permissions-helpers';
import { quietly, uniqueTestName } from '../utils/fixture-helpers';

const AUTHORIZATION_DETAIL_TYPE_PERMISSION = 'domain_authorization_detail_type';
// The domain Settings area is guarded by DOMAIN_SETTINGS[READ], which DOMAIN_USER does not hold.
const OPEN_DOMAIN_SETTINGS = 'domain_settings_read';

/**
 * The world AM-8009 describes: one domain holding one type with a consent expiry, and a user per
 * level of authorization detail type permission. Every role starts from DOMAIN_USER's permissions
 * plus what opens the domain's Settings, so the only difference between them is the RAR permission.
 */
export interface AuthorizationDetailTypePermissionsWorld {
  domain: Domain;
  paymentInitiation: AuthorizationDetailType;
  /** LIST only, as DOMAIN_USER holds it. */
  listUser: ConsolePersona;
  /** LIST and READ. */
  readUser: ConsolePersona;
  /** No authorization detail type permission at all. */
  noneUser: ConsolePersona;
}

export type AuthorizationDetailTypePermissionsFixtures = {
  rarWorld: AuthorizationDetailTypePermissionsWorld;
  signInAs: (persona: ConsolePersona) => Promise<void>;
};

export const test = base.extend<AuthorizationDetailTypePermissionsFixtures>({
  rarWorld: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();

    // The domain is never started: the Console reads from the management API only.
    const domain = await quietly(() => createDomain(adminToken, uniqueTestName(workerScope('pw-rar')), 'Playwright RAR domain'));
    const paymentInitiation = await quietly(() =>
      createAuthorizationDetailType(domain.id, adminToken, { type: 'payment_initiation', name: 'Payment initiation', expiresIn: 3600 }),
    );

    const domainUser = await findOrganizationRoleByName(adminToken, 'DOMAIN_USER', ListRolesTypeEnum.Domain);
    // The role list leaves permissions out, so read the role itself.
    const domainUserPermissions = (await getOrganizationRole(adminToken, domainUser.id)).permissions;
    const withoutRar = domainUserPermissions.filter((permission) => !permission.startsWith(AUTHORIZATION_DETAIL_TYPE_PERMISSION));

    const roleWith = (label: string, rarAcls: string[]) =>
      quietly(() =>
        createCustomOrganizationRole(adminToken, uniqueTestName(workerScope(label)), NewRoleAssignableTypeEnum.Domain, [
          ...withoutRar,
          OPEN_DOMAIN_SETTINGS,
          ...rarAcls.map((acl) => `${AUTHORIZATION_DETAIL_TYPE_PERMISSION}_${acl}`),
        ]),
      );
    const roles = {
      list: await roleWith('adt-list', ['list']),
      read: await roleWith('adt-read', ['list', 'read']),
      none: await roleWith('adt-none', []),
    };

    const listUser = await createConsolePersona(adminToken, 'adtlist');
    const readUser = await createConsolePersona(adminToken, 'adtread');
    const noneUser = await createConsolePersona(adminToken, 'adtnone');
    await quietly(() => addDomainMembership(domain.id, adminToken, userMembership(listUser.userId, roles.list.id)));
    await quietly(() => addDomainMembership(domain.id, adminToken, userMembership(readUser.userId, roles.read.id)));
    await quietly(() => addDomainMembership(domain.id, adminToken, userMembership(noneUser.userId, roles.none.id)));

    await use({ domain, paymentInitiation, listUser, readUser, noneUser });

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
    for (const persona of [listUser, readUser, noneUser]) {
      await quietly(() => deleteOrganisationUser(adminToken, persona.userId)).catch(() => undefined);
    }
    for (const role of Object.values(roles)) {
      await quietly(() => deleteOrganizationRole(adminToken, role.id)).catch(() => undefined);
    }
  },

  signInAs: async ({ page }, use) => {
    await use(async (persona: ConsolePersona) => signInToConsole(page, persona));
  },
});

export { expect } from '@playwright/test';
