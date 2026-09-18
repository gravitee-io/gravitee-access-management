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
import { addOrganizationMembership, userMembership } from '@management-commands/membership-management-commands';
import { createCustomOrganizationRole, deleteOrganizationRole } from '@management-commands/role-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import { createPersona, Persona } from '../../permissions/fixtures/rbac-fixture';
import { AutomationClient } from './automation-client';
import { AutomationDataPlaneFixture, setupAutomationDataPlaneFixture } from './automation-dataplane-fixture';

/** The ACLs the DATA_PLANE permission is made of, as the role API spells them. */
export const DATA_PLANE_READ_ACLS = ['data_plane_read', 'data_plane_list'];
export const DATA_PLANE_ALL_ACLS = [...DATA_PLANE_READ_ACLS, 'data_plane_create', 'data_plane_update', 'data_plane_delete'];

/** A principal plus an Automation API client authenticated as them. */
export interface DataPlanePrincipal extends Persona {
  client: AutomationClient;
}

export interface AutomationDataPlanePermissionsFixture extends Fixture {
  /** Admin-backed fixture: reserves ids, cleans planes up, and its client is used for setup. */
  admin: AutomationDataPlaneFixture;
  /** Holds only the default ORGANIZATION_USER role (DATA_PLANE read + list). */
  plainUser: DataPlanePrincipal;
  /** Custom organization role carrying DATA_PLANE read + list only. */
  reader: DataPlanePrincipal;
  /** Custom organization role carrying every DATA_PLANE ACL and nothing else. */
  manager: DataPlanePrincipal;
}

const withClient = (persona: Persona): DataPlanePrincipal => ({ ...persona, client: new AutomationClient(persona.token) });

/**
 * Three organization users, each with a different DATA_PLANE allowance. Custom roles are
 * ORGANIZATION-assignable so a single membership grants them; `createPersona` already verifies
 * every token belongs to the user it was minted for.
 */
export const setupAutomationDataPlanePermissionsFixture = async (): Promise<AutomationDataPlanePermissionsFixture> => {
  const admin = await setupAutomationDataPlaneFixture();
  const adminToken = admin.adminToken;

  const readerRole = await createCustomOrganizationRole(adminToken, uniqueName('dp-reader', true), 'ORGANIZATION', DATA_PLANE_READ_ACLS);
  const managerRole = await createCustomOrganizationRole(adminToken, uniqueName('dp-manager', true), 'ORGANIZATION', DATA_PLANE_ALL_ACLS);
  expect(readerRole.id).toEqual(expect.any(String));
  expect(managerRole.id).toEqual(expect.any(String));

  const plainUser = await createPersona(adminToken, 'dp-plain');
  const reader = await createPersona(adminToken, 'dp-reader');
  const manager = await createPersona(adminToken, 'dp-manager');
  await addOrganizationMembership(adminToken, userMembership(reader.userId, readerRole.id));
  await addOrganizationMembership(adminToken, userMembership(manager.userId, managerRole.id));

  return {
    admin,
    plainUser: withClient(plainUser),
    reader: withClient(reader),
    manager: withClient(manager),
    cleanUp: async () => {
      await admin.cleanUp();
      for (const persona of [plainUser, reader, manager]) {
        await deleteOrganisationUser(adminToken, persona.userId).catch(() => undefined);
      }
      for (const role of [readerRole, managerRole]) {
        await deleteOrganizationRole(adminToken, role.id).catch(() => undefined);
      }
    },
  };
};
