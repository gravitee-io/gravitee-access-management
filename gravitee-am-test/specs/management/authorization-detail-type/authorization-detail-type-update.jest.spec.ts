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
  validSchema,
  waitForAuditReporter,
} from './fixtures/authorization-detail-type-fixture';
import {
  createAuthorizationDetailType,
  getAuthorizationDetailType,
  listAuthorizationDetailTypes,
  patchAuthorizationDetailType,
  patchAuthorizationDetailTypeJson,
} from '@management-commands/authorization-detail-type-management-commands';
import { auditIdsOfType, waitForNewAudit } from '@management-commands/audit-management-commands';
import { addDomainMembership, userMembership } from '@management-commands/membership-management-commands';
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import type { AuthorizationDetailType } from '@management-models/AuthorizationDetailType';
import { createPersona } from '../permissions/fixtures/rbac-fixture';

setup();

const UPDATED_AUDIT = 'AUTHORIZATION_DETAIL_TYPE_UPDATED';

let fixture: AuthorizationDetailTypeFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailTypeFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const createFullType = (): Promise<AuthorizationDetailType> => {
  const type = uniqueType();
  return createAuthorizationDetailType(
    fixture.domain.id,
    fixture.accessToken,
    newType(type, { description: 'Initiates a payment', schema: validSchema(type), discovery: false, expiresIn: 3600 }),
  );
};

const read = (existing: AuthorizationDetailType) => getAuthorizationDetailType(fixture.domain.id, fixture.accessToken, existing.id);

const expectRejected = async (existing: AuthorizationDetailType, body: Record<string, unknown>, message: string) => {
  const response = await patchAuthorizationDetailTypeJson(fixture.domain.id, fixture.accessToken, existing.id, body);

  expect(response.status).toEqual(400);
  expect(response.body.message).toContain(message);
  expect(await read(existing)).toEqual(existing);
};

describe('Update', () => {
  it(jira`should update every field together ${'AM-7964'}`, async () => {
    const existing = await createFullType();
    const schema = validSchema(existing.type, { title: 'Payment' });

    const updated = await patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, existing.id, {
      name: 'Renamed',
      description: 'New description',
      schema,
      discovery: true,
      expiresIn: 60,
    });

    expect(updated).toEqual({
      ...existing,
      name: 'Renamed',
      description: 'New description',
      schema,
      discovery: true,
      expiresIn: 60,
      updatedAt: expect.any(Number),
    });
    expect(updated.updatedAt).toBeGreaterThan(existing.updatedAt);
    expect(await read(existing)).toEqual(updated);
  });

  it(jira`should change only discovery when only discovery is sent ${'AM-7964'}`, async () => {
    const existing = await createFullType();
    expect(existing.discovery).toBe(false);

    await patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, existing.id, { discovery: true });

    expect(await read(existing)).toEqual({ ...existing, discovery: true, updatedAt: expect.any(Number) });
  });

  it(jira`should clear the optional fields sent as null ${'AM-7964'}`, async () => {
    const existing = await createFullType();

    const response = await patchAuthorizationDetailTypeJson(fixture.domain.id, fixture.accessToken, existing.id, {
      description: null,
      schema: null,
      expiresIn: null,
    });

    expect(response.status).toEqual(200);
    expect(await read(existing)).toEqual({
      ...existing,
      description: undefined,
      schema: undefined,
      expiresIn: undefined,
      updatedAt: expect.any(Number),
    });
  });

  it(jira`should leave every field unchanged for an empty body ${'AM-7964'}`, async () => {
    const existing = await createFullType();

    const response = await patchAuthorizationDetailTypeJson(fixture.domain.id, fixture.accessToken, existing.id, {});

    expect(response.status).toEqual(200);
    expect(await read(existing)).toEqual({ ...existing, updatedAt: expect.any(Number) });
  });
});

describe('Validation', () => {
  it(jira`should refuse to clear the name ${'AM-7922'}`, async () => {
    await expectRejected(await createFullType(), { name: null }, 'Name is required');
  });

  it(jira`should refuse to clear discovery ${'AM-7922'}`, async () => {
    await expectRejected(await createFullType(), { discovery: null }, "Discovery can't be cleared");
  });

  it(jira`should reject a blank name ${'AM-7922'}`, async () => {
    await expectRejected(await createFullType(), { name: '   ' }, 'Name is required');
  });

  it(jira`should reject a name longer than 255 characters ${'AM-7922'}`, async () => {
    await expectRejected(await createFullType(), { name: 'n'.repeat(256) }, 'Name must be at most 255 characters');
  });

  it(jira`should reject a description longer than 1024 characters ${'AM-7922'}`, async () => {
    await expectRejected(await createFullType(), { description: 'd'.repeat(1025) }, 'Description must be at most 1024 characters');
  });

  it(jira`should reject expiresIn below 1 ${'AM-7922'}`, async () => {
    await expectRejected(await createFullType(), { expiresIn: 0 }, 'Expires in must be greater than 0');
  });

  it(jira`should reject a type sent in the body ${'AM-7922'}`, async () => {
    await expectRejected(
      await createFullType(),
      { type: uniqueType('account_information'), name: 'Renamed' },
      'Property [type] is not recognized',
    );
  });

  it(jira`should reject a schema that is not a JSON object ${'AM-7923'}`, async () => {
    await expectRejected(await createFullType(), { schema: '[]' }, 'Schema must be a JSON object');
  });

  it(jira`should reject a schema that pins type to another value ${'AM-7923'}`, async () => {
    const existing = await createFullType();

    await expectRejected(existing, { schema: validSchema(uniqueType('account_information')) }, 'Schema must constrain "type"');
  });

  it(jira`should accept a schema when the type had none ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const existing = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type));

    const updated = await patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, existing.id, { schema: validSchema(type) });

    expect(updated.schema).toEqual(validSchema(type));
  });
});

describe('Not found', () => {
  it(jira`should return 404 for an unknown id ${'AM-7964'}`, async () => {
    await expect(
      patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, 'unknown-id', { name: 'Renamed' }),
    ).rejects.toMatchObject({ response: { status: 404 } });
  });

  it(jira`should return 404 for a type of another domain ${'AM-7921'}`, async () => {
    const other = await fixture.otherDomain();
    const existing = await createAuthorizationDetailType(other.id, fixture.accessToken, newType(uniqueType()));

    await expect(
      patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, existing.id, { name: 'Renamed' }),
    ).rejects.toMatchObject({ response: { status: 404 } });
    expect(await getAuthorizationDetailType(other.id, fixture.accessToken, existing.id)).toEqual(existing);
  });

  it(jira`should refuse a domain that does not exist ${'AM-7921'}`, async () => {
    // refused by the permission check, which cannot place an unknown domain in the environment
    await expect(
      patchAuthorizationDetailType('unknown-domain', fixture.accessToken, 'unknown-id', { name: 'Renamed' }),
    ).rejects.toMatchObject({ response: { status: 403 } });
  });
});

describe('Audit', () => {
  beforeAll(() => waitForAuditReporter(fixture), 60000);

  it(jira`should audit a successful update with the change ${'AM-7924'}`, async () => {
    const existing = await createFullType();
    const known = await auditIdsOfType(fixture.domain.id, fixture.accessToken, UPDATED_AUDIT);

    await patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, existing.id, { name: 'Renamed' });

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, UPDATED_AUDIT, 'SUCCESS', known);
    expect(audit).toMatchObject({
      referenceType: 'domain',
      referenceId: fixture.domain.id,
      actor: { alternativeId: process.env.AM_ADMIN_USERNAME },
      target: { id: existing.id, type: 'AUTHORIZATION_DETAIL_TYPE', alternativeId: existing.type },
    });
    expect(JSON.parse(audit.outcome.message)).toEqual([{ op: 'replace', path: '/name', value: 'Renamed' }]);
  });

  it(jira`should audit a rejected update ${'AM-7924'}`, async () => {
    const existing = await createFullType();
    const known = await auditIdsOfType(fixture.domain.id, fixture.accessToken, UPDATED_AUDIT);

    await patchAuthorizationDetailTypeJson(fixture.domain.id, fixture.accessToken, existing.id, { schema: '[]' });

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, UPDATED_AUDIT, 'FAILURE', known);
    expect(audit.outcome.message).toContain('Schema must be a JSON object');
  });
});

describe('Permissions', () => {
  it(jira`should refuse an update by a domain user ${'AM-7925'}`, async () => {
    const existing = await createFullType();
    const domainUserRole = await findOrganizationRoleByName(fixture.accessToken, 'DOMAIN_USER', ListRolesTypeEnum.Domain);
    const domainUser = await createPersona(fixture.accessToken, 'adt-domain-user');
    try {
      await addDomainMembership(fixture.domain.id, fixture.accessToken, userMembership(domainUser.userId, domainUserRole.id));
      // the membership took effect
      await expect(listAuthorizationDetailTypes(fixture.domain.id, domainUser.token)).resolves.toBeDefined();

      const response = await patchAuthorizationDetailTypeJson(fixture.domain.id, domainUser.token, existing.id, { name: 'Renamed' });

      expect(response.status).toEqual(403);
      expect(await read(existing)).toEqual(existing);
    } finally {
      await deleteOrganisationUser(fixture.accessToken, domainUser.userId);
    }
  });
});
