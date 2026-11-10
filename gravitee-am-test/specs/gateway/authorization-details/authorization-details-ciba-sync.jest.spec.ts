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
import { afterAll, beforeAll, describe, it } from '@jest/globals';
import {
  deleteAuthorizationDetailTypeRaw,
  patchAuthorizationDetailType,
} from '@management-commands/authorization-detail-type-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { AUTHORIZATION_DETAILS_TEST, paymentSchema, toDetailsParam } from './fixtures/authorization-details-fixture';
import {
  AuthorizationDetailsCibaFixture,
  expectAcknowledged,
  expectBackchannelError,
  setupAuthorizationDetailsCibaFixture,
} from './fixtures/authorization-details-ciba-fixture';

// New backchannel authentication requests use authorization detail type and application changes without a restart

setup(300000);

let fixture: AuthorizationDetailsCibaFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsCibaFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const REFERENCE_TYPES = [AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE, AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE];

const notAllowed = (type: string) => `authorization_details[0] of type '${type}' is not allowed for this client`;

describe('Backchannel authentication - authorization detail type changes', () => {
  it(jira`should accept a created type once enabled ${'AM-8105'}`, async () => {
    const type = uniqueName('created_type', true);
    const authorizationDetails = toDetailsParam({ type });

    await fixture.createType({ type, name: 'Created', discovery: true });
    expectBackchannelError(
      await fixture.authenticate({ authorization_details: authorizationDetails }),
      'invalid_authorization_details',
      notAllowed(type),
    );

    await fixture.enableTypes([...REFERENCE_TYPES, type]);
    try {
      expectAcknowledged(await fixture.authenticate({ authorization_details: authorizationDetails }));
    } finally {
      await fixture.enableTypes(REFERENCE_TYPES);
    }
  });

  it(jira`should apply an updated schema ${'AM-8105'}`, async () => {
    const type = uniqueName('updated_type', true);
    const authorizationDetails = toDetailsParam({ type });
    const created = await fixture.createType({ type, name: 'Updated', discovery: true });
    await fixture.enableTypes([...REFERENCE_TYPES, type]);
    try {
      expectAcknowledged(await fixture.authenticate({ authorization_details: authorizationDetails }));

      await waitForSyncAfter(fixture.hintDomain.domain.id, () =>
        patchAuthorizationDetailType(fixture.hintDomain.domain.id, fixture.accessToken, created.id, { schema: paymentSchema(type) }),
      );

      expectBackchannelError(
        await fixture.authenticate({ authorization_details: authorizationDetails }),
        'invalid_authorization_details',
        `authorization_details[0] of type '${type}' does not match its schema`,
      );
    } finally {
      await fixture.enableTypes(REFERENCE_TYPES);
    }
  });

  it(jira`should reject a deleted type ${'AM-8105'}`, async () => {
    const type = uniqueName('deleted_type', true);
    const authorizationDetails = toDetailsParam({ type });
    const created = await fixture.createType({ type, name: 'Deleted', discovery: true });
    await fixture.enableTypes([...REFERENCE_TYPES, type]);
    expectAcknowledged(await fixture.authenticate({ authorization_details: authorizationDetails }));

    await waitForSyncAfter(fixture.hintDomain.domain.id, () =>
      deleteAuthorizationDetailTypeRaw(fixture.hintDomain.domain.id, fixture.accessToken, created.id),
    );

    expectBackchannelError(
      await fixture.authenticate({ authorization_details: authorizationDetails }),
      'invalid_authorization_details',
      notAllowed(type),
    );
  });
});

describe('Backchannel authentication - application type changes', () => {
  it(jira`should stop accepting a type no longer enabled ${'AM-8105'}`, async () => {
    const authorizationDetails = toDetailsParam({ type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE });
    expectAcknowledged(await fixture.authenticate({ authorization_details: authorizationDetails }));

    await fixture.enableTypes([AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE]);
    try {
      expectBackchannelError(
        await fixture.authenticate({ authorization_details: authorizationDetails }),
        'invalid_authorization_details',
        notAllowed(AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE),
      );
    } finally {
      await fixture.enableTypes(REFERENCE_TYPES);
    }
  });
});
