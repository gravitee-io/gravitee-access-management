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
  listUserAuthorizationDetailConsents,
  listUsers,
  revokeUserAuthorizationDetailConsent,
  revokeUserAuthorizationDetailConsents,
  revokeUserScopeConsents,
} from '@management-commands/user-management-commands';
import { auditsOfType, AuditEntry } from '@management-commands/audit-management-commands';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { AuthorizationDetailApprovalEntity } from '@management-models/AuthorizationDetailApprovalEntity';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';
import { toDetailsParam } from '../../gateway/authorization-details/fixtures/authorization-details-fixture';
import {
  account,
  ConsentFixture,
  expectError,
  follow,
  isConsentPage,
  openConsentPage,
  payment,
  recordedConsent,
  setupConsentFixture,
  submitConsent,
  TestUser,
} from '../../gateway/authorization-details/fixtures/authorization-details-consent-fixture';

// Administrators revoke the authorization details a user approved or denied; the user's tokens are revoked with them

setup(200000);

const REVOKED = 'USER_CONSENT_REVOKED';

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
  await waitForAuditReporter();
}, 60000);

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

/** The Management API drops a new domain's audits until its reporter is deployed. */
const waitForAuditReporter = () =>
  retryUntil(
    async () => {
      await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, { type: uniqueName('audit_probe', true), name: 'Probe' });
      return (await auditsOfType(fixture.domain.id, fixture.accessToken, 'AUTHORIZATION_DETAIL_TYPE_CREATED')).length;
    },
    (count) => count > 0,
    { timeoutMillis: 30000, intervalMillis: 1000 },
  );

const userIdOf = async (user: TestUser): Promise<string> => {
  const page = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
  expect(page.data).toHaveLength(1);
  return page.data[0].id;
};

const consentsOf = async (userId: string): Promise<AuthorizationDetailApprovalEntity[]> =>
  (await listUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId)).data;

const consentAndDeny = async (user: TestUser, approved: unknown[], denied: unknown[]): Promise<string> => {
  const session = await fixture.approve(user, approved);
  const tokens = await fixture.exchangeCode(session);
  const deniedPage = await openConsentPage(await fixture.authorize({ authorization_details: toDetailsParam(...denied) }, session.cookie));
  expectError(await follow(await submitConsent(deniedPage, false)), 'access_denied');
  return tokens.body.access_token;
};

const expectInactive = (token: string) =>
  retryUntil(
    () => fixture.introspect(token),
    (introspection) => introspection.active === false,
    { timeoutMillis: 15000, intervalMillis: 300 },
  );

const revocationAuditsOf = (userId: string): Promise<AuditEntry[]> =>
  retryUntil(
    async () => (await auditsOfType(fixture.domain.id, fixture.accessToken, REVOKED)).filter((audit) => audit.target?.id === userId),
    (audits) => audits.length > 0,
    { timeoutMillis: 30000, intervalMillis: 500 },
  );

describe('Revoking authorization detail consents in the Management API', () => {
  it(jira`should revoke one consent, the tokens for its application, and ask for consent again ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    const session = await fixture.approve(user, [payment(), account()]);
    const tokens = await fixture.exchangeCode(session);
    const userId = await userIdOf(user);
    const paymentConsent = (await consentsOf(userId)).find((consent) => consent.type === payment().type);

    await revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, userId, paymentConsent.id);

    expect((await consentsOf(userId)).map((consent) => consent.type)).toEqual([account().type]);
    await expectInactive(tokens.body.access_token);
    const again = await fixture.authorize({ authorization_details: toDetailsParam(payment()) }, session.cookie);
    expect(isConsentPage(again)).toBe(true);
  });

  it(jira`should audit the revocation with the administrator, the user and the revoked details ${'AM-8139'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment('EUR', '10.00')]);
    const userId = await userIdOf(user);
    const [consent] = await consentsOf(userId);

    await revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, userId, consent.id);

    const [audit] = await revocationAuditsOf(userId);
    expect(audit.outcome.status).toBe('success');
    expect(audit.actor.alternativeId).toBe('admin');
    expect(recordedConsent(audit)).toEqual([{ authorization_details: [payment('EUR', '10.00')], status: 'APPROVED' }]);
  });

  it(jira`should return 404 for another user's consent or an unknown consent, and keep the consent ${'AM-8138'}`, async () => {
    const owner = fixture.nextUser();
    await fixture.approve(owner, [payment()]);
    const ownerId = await userIdOf(owner);
    const [consent] = await consentsOf(ownerId);
    const otherUser = fixture.nextUser();
    await fixture.approve(otherUser, [account()]);
    const otherUserId = await userIdOf(otherUser);

    await expect(
      revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, otherUserId, consent.id),
    ).rejects.toMatchObject({ response: { status: 404 } });
    await expect(
      revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, ownerId, 'unknown-consent'),
    ).rejects.toMatchObject({ response: { status: 404 } });

    expect(await consentsOf(ownerId)).toHaveLength(1);
  });

  it(jira`should revoke the approved and denied consents for an application and its tokens ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    const accessToken = await consentAndDeny(user, [payment()], [account()]);
    const userId = await userIdOf(user);
    expect((await consentsOf(userId)).map((consent) => consent.status).sort()).toEqual(['approved', 'denied']);

    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId, 'another-client');
    expect(await consentsOf(userId)).toHaveLength(2);

    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId, fixture.clientId);

    expect(await consentsOf(userId)).toEqual([]);
    await expectInactive(accessToken);
  });

  it(jira`should revoke the consents for every application and all the tokens ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    const accessToken = await consentAndDeny(user, [payment()], [account()]);
    const userId = await userIdOf(user);

    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId);

    expect(await consentsOf(userId)).toEqual([]);
    await expectInactive(accessToken);
  });

  it(jira`should revoke for a user without authorization detail consents ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment()]);
    const userId = await userIdOf(user);
    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId);

    await expect(revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId)).resolves.toBeUndefined();
  });

  it(jira`should keep the authorization detail consents when the scope consents are revoked ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment()]);
    const userId = await userIdOf(user);

    await revokeUserScopeConsents(fixture.domain.id, fixture.accessToken, userId);

    expect(await consentsOf(userId)).toHaveLength(1);
  });
});
