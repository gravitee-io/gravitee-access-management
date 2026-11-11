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
import { setup } from '../../test-fixture';
import { listUserAuthorizationDetailConsents, listUserConsents, listUsers } from '@management-commands/user-management-commands';
import {
  createAuthorizationDetailType,
  deleteAuthorizationDetailTypeRaw,
} from '@management-commands/authorization-detail-type-management-commands';
import { AuthorizationDetailApprovalEntity } from '@management-models/AuthorizationDetailApprovalEntity';
import { uniqueName } from '@utils-commands/misc';
import { AUTHORIZATION_DETAILS_TEST, toDetailsParam } from '../../gateway/authorization-details/fixtures/authorization-details-fixture';
import {
  account,
  CONSENT_TEST,
  ConsentFixture,
  expectError,
  follow,
  openConsentPage,
  payment,
  setupConsentFixture,
  submitConsent,
  TestUser,
} from '../../gateway/authorization-details/fixtures/authorization-details-consent-fixture';

// The Management API lists the authorization details a user approved or rejected on the login flow

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const userIdOf = async (user: TestUser): Promise<string> => {
  const page = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
  expect(page.data).toHaveLength(1);
  return page.data[0].id;
};

const consentsOf = (userId: string, clientId?: string): Promise<AuthorizationDetailApprovalEntity[]> =>
  listUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId, clientId);

const byType = (consents: AuthorizationDetailApprovalEntity[], type: string): AuthorizationDetailApprovalEntity =>
  consents.find((consent) => consent.type === type);

describe('Authorization detail consents in the Management API', () => {
  it(jira`should list the approved details with their application and type ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment('EUR', '10.00'), account({ actions: ['list_accounts'] })]);

    const consents = await consentsOf(await userIdOf(user));

    expect(consents).toHaveLength(2);
    const paymentConsent = byType(consents, AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE);
    expect(paymentConsent.detail).toEqual(payment('EUR', '10.00'));
    expect(paymentConsent.status).toEqual('approved');
    expect(paymentConsent.clientId).toEqual(fixture.clientId);
    expect(paymentConsent.clientEntity).toMatchObject({ id: fixture.application.id, name: fixture.application.name });
    expect(paymentConsent.typeEntity).toEqual({
      type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
      name: 'Payment initiation',
      description: 'Initiate a payment from your account',
    });
    expect(paymentConsent.id).toBeDefined();
    expect(new Date(paymentConsent.updatedAt).getTime()).toBeLessThanOrEqual(Date.now());
    expect(new Date(paymentConsent.expiresAt).getTime()).toBeGreaterThan(Date.now());
    expect(byType(consents, AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE).detail).toEqual(account({ actions: ['list_accounts'] }));
  });

  it(jira`should list the rejected details as denied ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    const signedIn = await fixture.signIn(user, { authorization_details: toDetailsParam(payment()) });
    expectError(await follow(await submitConsent(await openConsentPage(signedIn), false)), 'access_denied');

    const consents = await consentsOf(await userIdOf(user));

    expect(consents).toHaveLength(1);
    expect(consents[0].status).toEqual('denied');
    expect(consents[0].detail).toEqual(payment());
  });

  it(jira`should only list the consents of the given application ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment()]);
    const userId = await userIdOf(user);

    expect(await consentsOf(userId, fixture.clientId)).toHaveLength(1);
    expect(await consentsOf(userId, 'another-client')).toEqual([]);
  });

  it(jira`should keep listing a consent after its type is deleted ${'AM-8127'}`, async () => {
    const type = uniqueName('deletable_type', true).replace(/-/g, '_');
    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, { type, name: 'Deletable' });
    await fixture.setApplicationAuthorizationDetailTypes([
      AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
      AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE,
      type,
    ]);
    const user = fixture.nextUser();
    await fixture.approve(user, [{ type }]);
    expect((await deleteAuthorizationDetailTypeRaw(fixture.domain.id, fixture.accessToken, created.id)).raw.status).toBe(204);

    const consents = await consentsOf(await userIdOf(user));

    expect(consents).toHaveLength(1);
    expect(consents[0].type).toEqual(type);
    expect(consents[0].typeEntity).toBeUndefined();
  });

  it(jira`should leave the scope consents out ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [account()], { scope: CONSENT_TEST.SCOPE });
    const userId = await userIdOf(user);

    const scopeConsents = await listUserConsents(fixture.domain.id, fixture.accessToken, userId);
    const detailConsents = await consentsOf(userId);

    expect(scopeConsents.map((consent) => consent.scope)).toEqual([CONSENT_TEST.SCOPE]);
    expect(detailConsents.map((consent) => consent.type)).toEqual([AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]);
  });

  it(jira`should return 404 for an unknown user ${'AM-8127'}`, async () => {
    await expect(consentsOf('unknown-user')).rejects.toMatchObject({ response: { status: 404 } });
  });
});
