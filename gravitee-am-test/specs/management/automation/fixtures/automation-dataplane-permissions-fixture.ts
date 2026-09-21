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

/** The read-only half of the DATA_PLANE permission, as the role API spells it. */
export const DATA_PLANE_READ_ACLS = ['data_plane_read', 'data_plane_list'];

/** A principal plus an Automation API client authenticated as them. */
export interface DataPlanePrincipal extends Persona {
  client: AutomationClient;
}

export interface AutomationDataPlanePermissionsFixture extends Fixture {
  /** Admin-backed fixture: reserves ids, cleans planes up, and its client is used for setup. */
  admin: AutomationDataPlaneFixture;
  /** Custom organization role carrying DATA_PLANE read + list only. */
  reader: DataPlanePrincipal;
}

/**
 * An organization user whose only DATA_PLANE allowance comes from a custom ORGANIZATION-assignable
 * role granted through a membership; `createPersona` verifies the token belongs to that user.
 */
export const setupAutomationDataPlanePermissionsFixture = async (): Promise<AutomationDataPlanePermissionsFixture> => {
  const admin = await setupAutomationDataPlaneFixture();
  const adminToken = admin.adminToken;

  const readerRole = await createCustomOrganizationRole(adminToken, uniqueName('dp-reader', true), 'ORGANIZATION', DATA_PLANE_READ_ACLS);
  expect(readerRole.id).toEqual(expect.any(String));

  const reader = await createPersona(adminToken, 'dp-reader');
  await addOrganizationMembership(adminToken, userMembership(reader.userId, readerRole.id));

  return {
    admin,
    reader: { ...reader, client: new AutomationClient(reader.token) },
    cleanUp: async () => {
      await admin.cleanUp();
      await deleteOrganisationUser(adminToken, reader.userId).catch(() => undefined);
      await deleteOrganizationRole(adminToken, readerRole.id).catch(() => undefined);
    },
  };
};
