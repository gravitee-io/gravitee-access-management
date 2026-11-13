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
  revokeUserAuthorizationDetailConsent,
  revokeUserAuthorizationDetailConsents,
  revokeUserScopeConsents,
} from '@management-commands/user-management-commands';
import { waitForAuditReporter } from '@management-commands/audit-management-commands';
import { toDetailsParam } from '../../gateway/authorization-details/fixtures/authorization-details-fixture';
import {
  account,
  isConsentPage,
  payment,
  recordedConsent,
} from '../../gateway/authorization-details/fixtures/authorization-details-consent-fixture';
import { setupUserConsentsFixture, UserConsentsFixture } from './fixtures/user-consents-fixture';

// Administrators revoke the authorization details a user approved or denied; the user's tokens are revoked with them

setup(200000);

let fixture: UserConsentsFixture;

beforeAll(async () => {
  fixture = await setupUserConsentsFixture();
  await waitForAuditReporter(fixture.domain.id, fixture.accessToken);
}, 60000);

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Revoking authorization detail consents in the Management API', () => {
  it(jira`should revoke one consent, the tokens for its application, and ask for consent again ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    const session = await fixture.approve(user, [payment(), account()]);
    const tokens = await fixture.exchangeCode(session);
    const userId = await fixture.userIdOf(user);
    const paymentConsent = (await fixture.consentsOf(userId)).find((consent) => consent.type === payment().type);

    await revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, userId, paymentConsent.id);

    expect((await fixture.consentsOf(userId)).map((consent) => consent.type)).toEqual([account().type]);
    await fixture.expectInactive(tokens.body.access_token);
    const again = await fixture.authorize({ authorization_details: toDetailsParam(payment()) }, session.cookie);
    expect(isConsentPage(again)).toBe(true);
  });

  it(jira`should audit the revocation with the administrator, the user and the revoked details ${'AM-8139'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment('EUR', '10.00')]);
    const userId = await fixture.userIdOf(user);
    const [consent] = await fixture.consentsOf(userId);

    await revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, userId, consent.id);

    const [audit] = await fixture.revocationAuditsOf(userId);
    expect(audit.outcome.status).toBe('success');
    expect(audit.actor.alternativeId).toBe('admin');
    expect(recordedConsent(audit)).toEqual([
      { clientId: fixture.clientId, authorization_details: [payment('EUR', '10.00')], status: 'APPROVED' },
    ]);
  });

  it(jira`should return 404 for another user's consent or an unknown consent, and keep the consent ${'AM-8138'}`, async () => {
    const owner = fixture.nextUser();
    await fixture.approve(owner, [payment()]);
    const ownerId = await fixture.userIdOf(owner);
    const [consent] = await fixture.consentsOf(ownerId);
    const otherUser = fixture.nextUser();
    await fixture.approve(otherUser, [account()]);
    const otherUserId = await fixture.userIdOf(otherUser);

    await expect(
      revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, otherUserId, consent.id),
    ).rejects.toMatchObject({ response: { status: 404 } });
    await expect(
      revokeUserAuthorizationDetailConsent(fixture.domain.id, fixture.accessToken, ownerId, 'unknown-consent'),
    ).rejects.toMatchObject({ response: { status: 404 } });

    expect(await fixture.consentsOf(ownerId)).toHaveLength(1);
  });

  it(jira`should revoke the approved and denied consents for an application and its tokens ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    const accessToken = await fixture.approveThenDeny(user, [payment()], [account()]);
    const userId = await fixture.userIdOf(user);
    expect((await fixture.consentsOf(userId)).map((consent) => consent.status).sort()).toEqual(['approved', 'denied']);

    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId, 'another-client');
    expect(await fixture.consentsOf(userId)).toHaveLength(2);

    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId, fixture.clientId);

    expect(await fixture.consentsOf(userId)).toEqual([]);
    await fixture.expectInactive(accessToken);
  });

  it(jira`should revoke the consents for every application and all the tokens ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    const accessToken = await fixture.approveThenDeny(user, [payment()], [account()]);
    const userId = await fixture.userIdOf(user);

    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId);

    expect(await fixture.consentsOf(userId)).toEqual([]);
    await fixture.expectInactive(accessToken);
  });

  it(jira`should revoke for a user without authorization detail consents ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment()]);
    const userId = await fixture.userIdOf(user);
    await revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId);

    await expect(revokeUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId)).resolves.toBeUndefined();
  });

  it(jira`should keep the authorization detail consents when the scope consents are revoked ${'AM-8138'}`, async () => {
    const user = fixture.nextUser();
    await fixture.approve(user, [payment()]);
    const userId = await fixture.userIdOf(user);

    await revokeUserScopeConsents(fixture.domain.id, fixture.accessToken, userId);

    expect(await fixture.consentsOf(userId)).toHaveLength(1);
  });
});
