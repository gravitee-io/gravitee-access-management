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
import {
  followUserAuthorizationDetailConsentsCursor,
  listUserAuthorizationDetailConsents,
  ListUserAuthorizationDetailConsentsParams,
  listUserConsentsDeprecated,
  listUserScopeConsents,
} from '@management-commands/user-management-commands';
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
  expectError,
  follow,
  openConsentPage,
  payment,
  submitConsent,
} from '../../gateway/authorization-details/fixtures/authorization-details-consent-fixture';
import { setupUserConsentsFixture, UserConsentsFixture } from './fixtures/user-consents-fixture';

// The Management API lists the authorization details a user approved or rejected on the login flow

setup(200000);

let fixture: UserConsentsFixture;

beforeAll(async () => {
  fixture = await setupUserConsentsFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const byType = (consents: AuthorizationDetailApprovalEntity[], type: string): AuthorizationDetailApprovalEntity =>
  consents.find((consent) => consent.type === type);

describe('Authorization detail consents in the Management API', () => {
  it(jira`should list the approved details with their application and type ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment('EUR', '10.00'), account({ actions: ['list_accounts'] })]);

    const consents = await fixture.consentsOf(await fixture.userIdOf(user));

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

    const consents = await fixture.consentsOf(await fixture.userIdOf(user));

    expect(consents).toHaveLength(1);
    expect(consents[0].status).toEqual('denied');
    expect(consents[0].detail).toEqual(payment());
  });

  it(jira`should only list the consents of the given application ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment()]);
    const userId = await fixture.userIdOf(user);

    expect(await fixture.consentsOf(userId, { clientId: fixture.clientId })).toHaveLength(1);
    expect(await fixture.consentsOf(userId, { clientId: 'another-client' })).toEqual([]);
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

    const consents = await fixture.consentsOf(await fixture.userIdOf(user));

    expect(consents).toHaveLength(1);
    expect(consents[0].type).toEqual(type);
    expect(consents[0].typeEntity).toBeUndefined();
  });

  it(jira`should only list the consents whose type matches, ignoring case and with wildcards ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment(), account()]);
    const userId = await fixture.userIdOf(user);

    const exact = await fixture.consentsOf(userId, { type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE.toUpperCase() });
    const wildcard = await fixture.consentsOf(userId, { type: '*' + AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE.slice(1, -1) + '*' });

    expect(exact.map((consent) => consent.type)).toEqual([AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]);
    expect(wildcard.map((consent) => consent.type)).toEqual([AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]);
  });

  it(jira`should page through the consents in the requested order ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment('EUR', '10.00'), account(), payment('EUR', '20.00')]);
    const userId = await fixture.userIdOf(user);

    const firstPage = await listUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId, {
      sort: 'type',
      dir: 'ASC',
      limit: 2,
    });
    expect(firstPage.totalCount).toEqual(3);
    expect(firstPage.page).toEqual(0);
    expect(firstPage.data).toHaveLength(2);
    expect(firstPage.nextCursor).toBeDefined();

    const secondPage = await followUserAuthorizationDetailConsentsCursor(
      fixture.domain.id,
      fixture.accessToken,
      userId,
      firstPage.nextCursor,
    );
    expect(secondPage.page).toEqual(1);
    expect(secondPage.nextCursor).toBeUndefined();

    const consents = [...firstPage.data, ...secondPage.data];
    expect(new Set(consents.map((consent) => consent.id)).size).toEqual(3);
    expect(consents.map((consent) => consent.type)).toEqual([
      AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE,
      AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
      AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
    ]);
  });

  it(jira`should reject an unknown sort field ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment()]);

    await expect(
      fixture.consentsOf(await fixture.userIdOf(user), { sort: 'detail' as ListUserAuthorizationDetailConsentsParams['sort'] }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it(jira`should leave the scope consents out ${'AM-8127'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [account()], { scope: CONSENT_TEST.SCOPE });
    const userId = await fixture.userIdOf(user);

    const scopeConsents = await listUserScopeConsents(fixture.domain.id, fixture.accessToken, userId);
    const detailConsents = await fixture.consentsOf(userId);

    expect(scopeConsents.map((consent) => consent.scope)).toEqual([CONSENT_TEST.SCOPE]);
    expect(detailConsents.map((consent) => consent.type)).toEqual([AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]);
  });

  it(jira`should list the scope consents on the scopes path and the deprecated one ${'AM-8129'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [account()], { scope: CONSENT_TEST.SCOPE });
    const userId = await fixture.userIdOf(user);

    const scopeConsents = await listUserScopeConsents(fixture.domain.id, fixture.accessToken, userId);
    const deprecatedScopeConsents = await listUserConsentsDeprecated(fixture.domain.id, fixture.accessToken, userId);

    expect(scopeConsents.map((consent) => consent.scope)).toEqual([CONSENT_TEST.SCOPE]);
    expect(deprecatedScopeConsents.map((consent) => consent.id)).toEqual(scopeConsents.map((consent) => consent.id));
  });

  it(jira`should return 404 for an unknown user ${'AM-8127'}`, async () => {
    await expect(fixture.consentsOf('unknown-user')).rejects.toMatchObject({ response: { status: 404 } });
  });
});
