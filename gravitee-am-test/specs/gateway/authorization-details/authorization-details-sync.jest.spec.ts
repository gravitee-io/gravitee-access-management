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
import {
  deleteAuthorizationDetailTypeRaw,
  patchAuthorizationDetailType,
} from '@management-commands/authorization-detail-type-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import {
  AuthorizationDetailsFixture,
  toDetailsParam,
  expectClientError,
  expectLoginPage,
  paymentSchema,
  setupAuthorizationDetailsFixture,
} from './fixtures/authorization-details-fixture';

// The gateway applies authorization detail type and application changes without a restart

setup(200000);

let fixture: AuthorizationDetailsFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsFixture({ withTypes: false });
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const notAllowed = (type: string) => `authorization_details[0] of type '${type}' is not allowed for this client`;

describe('Gateway - authorization detail type changes', () => {
  it(jira`should accept and advertise a created type ${'AM-8050'}`, async () => {
    const type = uniqueName('created_type', true);

    await fixture.createType({ type, name: 'Created', discovery: true });
    await fixture.enableTypes([type]);

    expectLoginPage(await fixture.authorize({ authorization_details: toDetailsParam({ type }) }));
    expect((await fixture.discovery()).authorization_details_types_supported).toContain(type);
  });

  it(jira`should apply an updated schema and discovery flag ${'AM-8050'}`, async () => {
    const type = uniqueName('updated_type', true);
    const created = await fixture.createType({ type, name: 'Updated', discovery: true });
    await fixture.enableTypes([type]);

    await waitForSyncAfter(fixture.domain.id, () =>
      patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, created.id, { schema: paymentSchema(type), discovery: false }),
    );

    expectClientError(
      await fixture.authorize({ authorization_details: toDetailsParam({ type }) }),
      'invalid_authorization_details',
      `authorization_details[0] of type '${type}' does not match its schema`,
    );
    expect((await fixture.discovery()).authorization_details_types_supported ?? []).not.toContain(type);
  });

  it(jira`should reject and stop advertising a deleted type ${'AM-8050'}`, async () => {
    const type = uniqueName('deleted_type', true);
    const created = await fixture.createType({ type, name: 'Deleted', discovery: true });
    await fixture.enableTypes([type]);

    await waitForSyncAfter(fixture.domain.id, () => deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, created.id));

    expectClientError(
      await fixture.authorize({ authorization_details: toDetailsParam({ type }) }),
      'invalid_authorization_details',
      notAllowed(type),
    );
    expect((await fixture.discovery()).authorization_details_types_supported ?? []).not.toContain(type);
  });
});

describe('Gateway - application enabled type changes', () => {
  it(jira`should accept a type once the application enables it ${'AM-8050'}`, async () => {
    const type = uniqueName('enabled_type', true);
    await fixture.createType({ type, name: 'Enabled' });
    await fixture.enableTypes([]);

    await fixture.enableTypes([type]);

    expectLoginPage(await fixture.authorize({ authorization_details: toDetailsParam({ type }) }));
  });

  it(jira`should reject a type once the application disables it ${'AM-8050'}`, async () => {
    const type = uniqueName('disabled_type', true);
    await fixture.createType({ type, name: 'Disabled' });
    await fixture.enableTypes([type]);

    await fixture.enableTypes([]);

    expectClientError(
      await fixture.authorize({ authorization_details: toDetailsParam({ type }) }),
      'invalid_authorization_details',
      notAllowed(type),
    );
  });
});
