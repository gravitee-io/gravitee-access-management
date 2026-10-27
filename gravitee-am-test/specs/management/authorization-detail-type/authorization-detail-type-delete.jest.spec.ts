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
import { jira } from '@specs-utils/jira';
import {
  AuthorizationDetailTypeFixture,
  newType,
  setupAuthorizationDetailTypeFixture,
  uniqueType,
  waitForAuditReporter,
} from './fixtures/authorization-detail-type-fixture';
import {
  createAuthorizationDetailType,
  deleteAuthorizationDetailTypeRaw,
  getAuthorizationDetailType,
  listAuthorizationDetailTypes,
} from '@management-commands/authorization-detail-type-management-commands';
import { auditIdsOfType, waitForNewAudit } from '@management-commands/audit-management-commands';
import { addDomainMembership, userMembership } from '@management-commands/membership-management-commands';
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import type { AuthorizationDetailType } from '@management-models/AuthorizationDetailType';
import { createPersona } from '../permissions/fixtures/rbac-fixture';

setup();

const DELETED_AUDIT = 'AUTHORIZATION_DETAIL_TYPE_DELETED';

let fixture: AuthorizationDetailTypeFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailTypeFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const createType = (type = uniqueType()): Promise<AuthorizationDetailType> =>
  createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type));

const read = (existing: AuthorizationDetailType) => getAuthorizationDetailType(fixture.domain.id, fixture.accessToken, existing.id);

describe('Delete', () => {
  it(jira`should delete a type so it can no longer be read or listed ${'AM-7965'}`, async () => {
    const existing = await createType();

    const response = await deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, existing.id);

    expect(response.raw.status).toEqual(204);
    await expect(read(existing)).rejects.toMatchObject({ response: { status: 404 } });
    const page = await listAuthorizationDetailTypes(fixture.domain.id, fixture.accessToken, { q: existing.type });
    expect(page.data).toEqual([]);
  });

  it(jira`should accept the same type again once deleted ${'AM-7965'}`, async () => {
    const existing = await createType();
    await deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, existing.id);

    const recreated = await createType(existing.type);

    expect(recreated.type).toEqual(existing.type);
    expect(recreated.id).not.toEqual(existing.id);
  });
});

describe('Not found', () => {
  it(jira`should return 404 for an unknown id ${'AM-7965'}`, async () => {
    await expect(deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, 'unknown-id')).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  it(jira`should return 404 for a type of another domain ${'AM-7921'}`, async () => {
    const other = await fixture.otherDomain();
    const existing = await createAuthorizationDetailType(other.id, fixture.accessToken, newType(uniqueType()));

    await expect(deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, existing.id)).rejects.toMatchObject({
      response: { status: 404 },
    });
    expect(await getAuthorizationDetailType(other.id, fixture.accessToken, existing.id)).toEqual(existing);
  });

  it(jira`should refuse a domain that does not exist ${'AM-7921'}`, async () => {
    await expect(deleteAuthorizationDetailTypeRaw('unknown-domain', fixture.accessToken, 'unknown-id')).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});

describe('Audit', () => {
  beforeAll(() => waitForAuditReporter(fixture), 60000);

  it(jira`should audit a successful delete ${'AM-7924'}`, async () => {
    const existing = await createType();
    const known = await auditIdsOfType(fixture.domain.id, fixture.accessToken, DELETED_AUDIT);

    await deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, existing.id);

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, DELETED_AUDIT, 'SUCCESS', known);
    expect(audit).toMatchObject({
      referenceType: 'domain',
      referenceId: fixture.domain.id,
      actor: { alternativeId: process.env.AM_ADMIN_USERNAME },
      target: { id: existing.id, type: 'AUTHORIZATION_DETAIL_TYPE', alternativeId: existing.type },
    });
  });
});

describe('Permissions', () => {
  it(jira`should refuse a delete by a domain user ${'AM-7925'}`, async () => {
    const existing = await createType();
    const domainUserRole = await findOrganizationRoleByName(fixture.accessToken, 'DOMAIN_USER', ListRolesTypeEnum.Domain);
    const domainUser = await createPersona(fixture.accessToken, 'adt-domain-user');
    try {
      await addDomainMembership(fixture.domain.id, fixture.accessToken, userMembership(domainUser.userId, domainUserRole.id));
      // the membership took effect
      await expect(listAuthorizationDetailTypes(fixture.domain.id, domainUser.token)).resolves.toBeDefined();

      await expect(deleteAuthorizationDetailTypeRaw(fixture.domain.id, domainUser.token, existing.id)).rejects.toMatchObject({
        response: { status: 403 },
      });
      expect(await read(existing)).toEqual(existing);
    } finally {
      await deleteOrganisationUser(fixture.accessToken, domainUser.userId);
    }
  });
});
