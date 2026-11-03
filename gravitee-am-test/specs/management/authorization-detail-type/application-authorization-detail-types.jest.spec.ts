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
  AuthorizationDetailTypeFixture,
  createTestApplication,
  newType,
  readApplicationTypes,
  setApplicationTypes,
  setupAuthorizationDetailTypeFixture,
  uniqueType,
  waitForApplicationUpdateOf,
  waitForAuditReporter,
} from './fixtures/authorization-detail-type-fixture';
import {
  createAuthorizationDetailType,
  deleteAuthorizationDetailTypeRaw,
} from '@management-commands/authorization-detail-type-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { auditIdsOfType, waitForNewAudit } from '@management-commands/audit-management-commands';
import { ApplicationAuthorizationDetailTypeSettings } from '@management-models/ApplicationAuthorizationDetailTypeSettings';
import { Application } from '@management-models/Application';

setup();

let fixture: AuthorizationDetailTypeFixture;
let paymentType: string;
let accountType: string;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailTypeFixture();
  paymentType = (await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType()))).type;
  accountType = (await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType('account_information'))))
    .type;
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const createApp = (): Promise<Application> => createTestApplication(fixture);

const setTypes = (app: Application, settings: ApplicationAuthorizationDetailTypeSettings[]) => setApplicationTypes(fixture, app, settings);

const readTypes = (app: Application) => readApplicationTypes(fixture, app);

describe('Enabling authorization detail types', () => {
  it(jira`should return an empty list when no type is enabled ${'AM-8023'}`, async () => {
    const app = await createApp();

    expect(await readTypes(app)).toEqual([]);
  });

  it(jira`should enable types with and without an expiry override ${'AM-8023'}`, async () => {
    const app = await createApp();

    await setTypes(app, [{ type: paymentType, expiresIn: 600 }, { type: accountType }]);

    expect(await readTypes(app)).toEqual(expect.arrayContaining([{ type: paymentType, expiresIn: 600 }, { type: accountType }]));
    expect(await readTypes(app)).toHaveLength(2);
  });

  it(jira`should change only the expiry of the patched type ${'AM-8023'}`, async () => {
    const app = await createApp();
    await setTypes(app, [
      { type: paymentType, expiresIn: 600 },
      { type: accountType, expiresIn: 60 },
    ]);

    await setTypes(app, [
      { type: paymentType, expiresIn: 1200 },
      { type: accountType, expiresIn: 60 },
    ]);

    expect(await readTypes(app)).toEqual(
      expect.arrayContaining([
        { type: paymentType, expiresIn: 1200 },
        { type: accountType, expiresIn: 60 },
      ]),
    );
  });

  it(jira`should replace the list as a whole ${'AM-8023'}`, async () => {
    const app = await createApp();
    await setTypes(app, [
      { type: paymentType, expiresIn: 600 },
      { type: accountType, expiresIn: 60 },
    ]);

    await setTypes(app, [{ type: accountType, expiresIn: 60 }]);

    expect(await readTypes(app)).toEqual([{ type: accountType, expiresIn: 60 }]);
  });

  it(jira`should leave the list unchanged when the field is omitted ${'AM-8023'}`, async () => {
    const app = await createApp();
    await setTypes(app, [{ type: paymentType, expiresIn: 600 }]);

    await patchApplication(fixture.domain.id, fixture.accessToken, { settings: { oauth: { accessTokenValiditySeconds: 3600 } } }, app.id);

    expect(await readTypes(app)).toEqual([{ type: paymentType, expiresIn: 600 }]);
  });
});

describe('Removing authorization detail types', () => {
  it(jira`should remove every type with an empty list ${'AM-8023'}`, async () => {
    const app = await createApp();
    await setTypes(app, [{ type: paymentType, expiresIn: 600 }, { type: accountType }]);

    await setTypes(app, []);

    expect(await readTypes(app)).toEqual([]);
  });
});

describe('Validation', () => {
  const expectRejected = async (settings: ApplicationAuthorizationDetailTypeSettings[], message: string) => {
    const app = await createApp();
    await setTypes(app, [{ type: paymentType, expiresIn: 600 }]);

    await expect(setTypes(app, settings)).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining(message),
    });
    expect(await readTypes(app)).toEqual([{ type: paymentType, expiresIn: 600 }]);
  };

  it(jira`should reject a type that does not exist in the domain ${'AM-8025'}`, async () => {
    await expectRejected([{ type: 'unknown_type' }], "type 'unknown_type' is not an authorization detail type of the domain");
  });

  it(jira`should compare types exactly, case included ${'AM-8025'}`, async () => {
    const upperCased = paymentType.toUpperCase();
    await expectRejected([{ type: upperCased }], `type '${upperCased}' is not an authorization detail type of the domain`);
  });

  it(jira`should reject a type that appears twice ${'AM-8025'}`, async () => {
    await expectRejected([{ type: paymentType }, { type: paymentType, expiresIn: 60 }], `type '${paymentType}' appears more than once`);
  });

  it(jira`should reject a missing type ${'AM-8025'}`, async () => {
    await expectRejected([{ expiresIn: 60 } as ApplicationAuthorizationDetailTypeSettings], 'type is required');
  });

  it(jira`should reject an empty type ${'AM-8025'}`, async () => {
    await expectRejected([{ type: '' }], 'type is required');
  });

  it(jira`should reject an expiry below 1 ${'AM-8025'}`, async () => {
    await expectRejected([{ type: paymentType, expiresIn: 0 }], `expiresIn 0 of type '${paymentType}' must be at least 1`);
  });

  it(jira`should accept an update of the remaining types after a type is deleted from the domain ${'AM-8025'}`, async () => {
    const deleted = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType('deleted_type')));
    const app = await createApp();
    await setTypes(app, [{ type: deleted.type, expiresIn: 600 }, { type: paymentType }]);
    expect((await deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, deleted.id)).raw.status).toBe(204);
    expect(await readTypes(app)).toEqual([{ type: paymentType }]);

    await setTypes(app, [{ type: paymentType, expiresIn: 1200 }]);

    expect(await readTypes(app)).toEqual([{ type: paymentType, expiresIn: 1200 }]);
  });
});

describe('Audit', () => {
  beforeAll(() => waitForAuditReporter(fixture), 60000);

  it(jira`should audit a successful update ${'AM-8024'}`, async () => {
    const app = await createApp();
    const known = await auditIdsOfType(fixture.domain.id, fixture.accessToken, APPLICATION_UPDATED_AUDIT);

    await setTypes(app, [{ type: paymentType, expiresIn: 600 }]);

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, APPLICATION_UPDATED_AUDIT, 'SUCCESS', known);
    expect(audit).toMatchObject({
      actor: { alternativeId: process.env.AM_ADMIN_USERNAME },
      target: { id: app.id, type: 'APPLICATION' },
    });
  });

  it(jira`should audit a rejected update ${'AM-8024'}`, async () => {
    const app = await createApp();
    const known = await auditIdsOfType(fixture.domain.id, fixture.accessToken, APPLICATION_UPDATED_AUDIT);

    await expect(setTypes(app, [{ type: paymentType, expiresIn: 0 }])).rejects.toMatchObject({ response: { status: 400 } });

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, APPLICATION_UPDATED_AUDIT, 'FAILURE', known);
    expect(audit.outcome.message).toContain('must be at least 1');
  });

  it(jira`should audit a removal as an application update by the user ${'AM-8024'}`, async () => {
    const app = await createApp();
    await setTypes(app, [{ type: paymentType, expiresIn: 600 }, { type: accountType }]);
    // the setup update is audited too, and asynchronously
    const known = new Set([(await waitForApplicationUpdateOf(fixture, app, new Set())).id]);

    await setTypes(app, [{ type: accountType }]);

    expect(await waitForApplicationUpdateOf(fixture, app, known)).toMatchObject({
      referenceType: 'domain',
      referenceId: fixture.domain.id,
      actor: { alternativeId: process.env.AM_ADMIN_USERNAME },
      target: { id: app.id, type: 'APPLICATION' },
    });
  });
});
