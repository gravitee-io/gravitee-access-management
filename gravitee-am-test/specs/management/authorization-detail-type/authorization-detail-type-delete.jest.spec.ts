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
  APPLICATION_UPDATED_AUDIT,
  applicationUpdatesOf,
  AuthorizationDetailTypeFixture,
  createTestApplicationWithTypes,
  newType,
  readApplicationTypes,
  setupAuthorizationDetailTypeFixture,
  SYSTEM_ACTOR,
  uniqueType,
  waitForApplicationUpdateOf,
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
import { Application } from '@management-models/Application';
import { ApplicationAuthorizationDetailTypeSettings } from '@management-models/ApplicationAuthorizationDetailTypeSettings';
import { Domain } from '@management-models/Domain';
import { createPersona } from '../permissions/fixtures/rbac-fixture';

setup();

const DELETED_AUDIT = 'AUTHORIZATION_DETAIL_TYPE_DELETED';

let fixture: AuthorizationDetailTypeFixture;
let keptType: string;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailTypeFixture();
  keptType = (await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType('kept_type')))).type;
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const createType = (type = uniqueType(), domain: Domain = fixture.domain): Promise<AuthorizationDetailType> =>
  createAuthorizationDetailType(domain.id, fixture.accessToken, newType(type));

const deleteType = async (existing: AuthorizationDetailType): Promise<number> =>
  (await deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, existing.id)).raw.status;

const appWithTypes = (settings: ApplicationAuthorizationDetailTypeSettings[], domain?: Domain): Promise<Application> =>
  createTestApplicationWithTypes(fixture, settings, domain);

const readTypes = (app: Application) => readApplicationTypes(fixture, app);

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

describe('Removal from applications', () => {
  it(jira`should remove the type and its expiry override from every application that has it ${'AM-8030'}`, async () => {
    const deleted = await createType();
    const withOtherType = await appWithTypes([
      { type: deleted.type, expiresIn: 600 },
      { type: keptType, expiresIn: 60 },
    ]);
    const withTypeOnly = await appWithTypes([{ type: deleted.type }]);

    expect(await deleteType(deleted)).toEqual(204);

    expect(await readTypes(withOtherType)).toEqual([{ type: keptType, expiresIn: 60 }]);
    expect(await readTypes(withTypeOnly)).toEqual([]);
  });

  it(jira`should leave an application without the type unchanged ${'AM-8030'}`, async () => {
    const deleted = await createType();
    await appWithTypes([{ type: deleted.type }]);
    const appWithoutType = await appWithTypes([{ type: keptType, expiresIn: 60 }]);

    expect(await deleteType(deleted)).toEqual(204);

    expect(await readTypes(appWithoutType)).toEqual([{ type: keptType, expiresIn: 60 }]);
  });

  it(jira`should leave an application of another domain with the same type unchanged ${'AM-8030'}`, async () => {
    const other = await fixture.otherDomain();
    const deleted = await createType();
    const sameTypeElsewhere = await createType(deleted.type, other);
    const otherApp = await appWithTypes([{ type: deleted.type, expiresIn: 600 }], other);

    expect(await deleteType(deleted)).toEqual(204);

    expect(await readTypes(otherApp)).toEqual([{ type: deleted.type, expiresIn: 600 }]);
    expect(await getAuthorizationDetailType(other.id, fixture.accessToken, sameTypeElsewhere.id)).toEqual(sameTypeElsewhere);
  });

  it(jira`should not enable a re-created type on the applications it was removed from ${'AM-8030'}`, async () => {
    const deleted = await createType();
    const app = await appWithTypes([
      { type: deleted.type, expiresIn: 600 },
      { type: keptType, expiresIn: 60 },
    ]);
    await deleteType(deleted);

    await createType(deleted.type);

    expect(await readTypes(app)).toEqual([{ type: keptType, expiresIn: 60 }]);
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

  it(jira`should audit an update by the system for each application the type is removed from ${'AM-8024'}`, async () => {
    const deleted = await createType();
    const first = await appWithTypes([{ type: deleted.type, expiresIn: 600 }]);
    const second = await appWithTypes([{ type: deleted.type }, { type: keptType }]);
    const known = await auditIdsOfType(fixture.domain.id, fixture.accessToken, APPLICATION_UPDATED_AUDIT);

    await deleteType(deleted);

    for (const app of [first, second]) {
      expect(await waitForApplicationUpdateOf(fixture, app, known, SYSTEM_ACTOR)).toMatchObject({
        referenceType: 'domain',
        referenceId: fixture.domain.id,
        actor: { id: SYSTEM_ACTOR, type: SYSTEM_ACTOR, alternativeId: SYSTEM_ACTOR, displayName: SYSTEM_ACTOR },
        target: { id: app.id, type: 'APPLICATION' },
      });
    }
  });

  it(jira`should not audit an update of an application without the type ${'AM-8024'}`, async () => {
    const deleted = await createType();
    const changed = await appWithTypes([{ type: deleted.type }]);
    const unchanged = await appWithTypes([{ type: keptType }]);
    const known = await auditIdsOfType(fixture.domain.id, fixture.accessToken, APPLICATION_UPDATED_AUDIT);

    await deleteType(deleted);

    // the changed application's audit arrived, so a cleanup audit of the other would have too
    await waitForApplicationUpdateOf(fixture, changed, known, SYSTEM_ACTOR);
    expect(await applicationUpdatesOf(fixture, unchanged, known, SYSTEM_ACTOR)).toEqual([]);
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
