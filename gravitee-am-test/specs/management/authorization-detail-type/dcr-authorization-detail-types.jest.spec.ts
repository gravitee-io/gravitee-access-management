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
import { jira } from '@specs-utils/jira';
import { getApplication, patchApplication } from '@management-commands/application-management-commands';
import { auditIdsOfType, waitForNewAudit } from '@management-commands/audit-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { ApplicationAuthorizationDetailTypeSettings } from '@management-models/ApplicationAuthorizationDetailTypeSettings';
import { setup } from '../../test-fixture';
import { AUTHORIZATION_DETAILS_TEST } from '../../gateway/authorization-details/fixtures/authorization-details-fixture';
import {
  AuthorizationDetailsDcrFixture,
  RegisteredClient,
  setupAuthorizationDetailsDcrFixture,
} from '../../gateway/authorization-details/fixtures/authorization-details-dcr-fixture';

// Applications created and updated through DCR with authorization_details_types, as the Management API sees them

setup(200000);

const { PAYMENT_TYPE, ACCOUNT_TYPE } = AUTHORIZATION_DETAILS_TEST;

let fixture: AuthorizationDetailsDcrFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsDcrFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const applicationTypes = async (client: RegisteredClient): Promise<ApplicationAuthorizationDetailTypeSettings[]> =>
  (await getApplication(fixture.domain.id, fixture.accessToken, client.applicationId)).settings.oauth.authorizationDetailTypes;

const overrideExpiry = (client: RegisteredClient, settings: ApplicationAuthorizationDetailTypeSettings[]) =>
  waitForSyncAfter(fixture.domain.id, () =>
    patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { oauth: { authorizationDetailTypes: settings } } },
      client.applicationId,
    ),
  );

const expectAuditOf = async (type: string, client: RegisteredClient, action: () => Promise<unknown>) => {
  const knownIds = await auditIdsOfType(fixture.domain.id, fixture.accessToken, type);
  await action();
  const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, type, 'SUCCESS', knownIds);
  expect(audit.target?.id).toEqual(client.applicationId);
};

describe('Management API - authorization detail types of DCR applications', () => {
  it(jira`should enable registered types without an expiry override ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE, ACCOUNT_TYPE] });

    expect(await applicationTypes(client)).toEqual([{ type: PAYMENT_TYPE }, { type: ACCOUNT_TYPE }]);
  });

  it(jira`should keep the expiry override of the template types ${'AM-8149'}`, async () => {
    const template = await fixture.createTemplate([{ type: PAYMENT_TYPE, expiresIn: 600 }]);

    const client = await fixture.registerClient({ software_id: template.id });

    expect(await applicationTypes(client)).toEqual([{ type: PAYMENT_TYPE, expiresIn: 600 }]);
  });

  it(jira`should keep the expiry override of a template type the request lists ${'AM-8149'}`, async () => {
    const template = await fixture.createTemplate([{ type: PAYMENT_TYPE, expiresIn: 600 }]);

    const client = await fixture.registerClient({ software_id: template.id, authorization_details_types: [PAYMENT_TYPE, ACCOUNT_TYPE] });

    expect(await applicationTypes(client)).toEqual([{ type: PAYMENT_TYPE, expiresIn: 600 }, { type: ACCOUNT_TYPE }]);
  });

  it(jira`should keep an administrator expiry override of a type a DCR update lists ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });
    await overrideExpiry(client, [{ type: PAYMENT_TYPE, expiresIn: 600 }]);

    await fixture.patch(client, { authorization_details_types: [PAYMENT_TYPE, ACCOUNT_TYPE] }).expect(200);

    expect(await applicationTypes(client)).toEqual([{ type: PAYMENT_TYPE, expiresIn: 600 }, { type: ACCOUNT_TYPE }]);
  });

  it(jira`should keep an administrator expiry override when a DCR update omits the types ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });
    await overrideExpiry(client, [{ type: PAYMENT_TYPE, expiresIn: 600 }]);

    await fixture.patch(client, { client_name: 'renamed' }).expect(200);

    expect(await applicationTypes(client)).toEqual([{ type: PAYMENT_TYPE, expiresIn: 600 }]);
  });
});

describe('Management API - audits of DCR applications with authorization detail types', () => {
  it('should audit the creation of the application', async () => {
    const knownIds = await auditIdsOfType(fixture.domain.id, fixture.accessToken, 'APPLICATION_CREATED');

    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, 'APPLICATION_CREATED', 'SUCCESS', knownIds);
    expect(audit.target?.id).toEqual(client.applicationId);
  });

  it('should audit the update of the application', async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    await expectAuditOf('APPLICATION_UPDATED', client, () =>
      fixture.patch(client, { authorization_details_types: [ACCOUNT_TYPE] }).expect(200),
    );
  });

  it('should audit the deletion of the application', async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    await expectAuditOf('APPLICATION_DELETED', client, () => fixture.remove(client).expect(204));
  });
});
