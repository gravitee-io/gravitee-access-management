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
import {
  createAuthorizationDetailType,
  deleteAuthorizationDetailTypeRaw,
  patchAuthorizationDetailType,
} from '@management-commands/authorization-detail-type-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { AUTHORIZATION_DETAILS_TEST, paymentSchema, toDetailsParam } from './fixtures/authorization-details-fixture';
import { ConsentFixture, expectCode, setupConsentFixture } from './fixtures/authorization-details-consent-fixture';

// Introspection returns the details as granted, whatever happens to their type afterwards

setup(200000);

const FIXTURE_TYPES = [AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE, AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE];

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture({ skipConsent: true });
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

/** Creates a type without a schema, enables it on the application and issues tokens granting one detail of it. */
const issueTokensForNewType = async () => {
  const type = uniqueName('introspected_type', true);
  const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, { type, name: 'Introspected' });
  await fixture.setApplicationAuthorizationDetailTypes([...FIXTURE_TYPES, type]);
  const details = [{ type, actions: ['read'], instructedAmount: 'any value' }];
  const callback = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(...details) });
  expectCode(callback);
  const tokens = (await fixture.exchangeCode(callback)).body;
  return { type, typeId: created.id, details, tokens };
};

const expectGranted = async (tokens: any, details: unknown[]) => {
  expect((await fixture.introspect(tokens.access_token)).authorization_details).toEqual(details);
  expect((await fixture.introspect(tokens.refresh_token, 'refresh_token')).authorization_details).toEqual(details);
};

describe('Introspection - authorization detail type changes after issuance', () => {
  it(jira`should return the details as granted after the type is updated ${'AM-8088'}`, async () => {
    const { type, typeId, details, tokens } = await issueTokensForNewType();

    // the payment schema rejects the granted detail, which has no valid instructedAmount
    await waitForSyncAfter(fixture.domain.id, () =>
      patchAuthorizationDetailType(fixture.domain.id, fixture.accessToken, typeId, { name: 'Updated', schema: paymentSchema(type) }),
    );

    await expectGranted(tokens, details);
  });

  it(jira`should return the details as granted after the application disables the type ${'AM-8088'}`, async () => {
    const { details, tokens } = await issueTokensForNewType();

    await fixture.setApplicationAuthorizationDetailTypes(FIXTURE_TYPES);

    await expectGranted(tokens, details);
  });

  it(jira`should return the details as granted after the type is deleted from the domain ${'AM-8088'}`, async () => {
    const { typeId, details, tokens } = await issueTokensForNewType();

    await waitForSyncAfter(fixture.domain.id, () => deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, typeId));

    await expectGranted(tokens, details);
  });
});
