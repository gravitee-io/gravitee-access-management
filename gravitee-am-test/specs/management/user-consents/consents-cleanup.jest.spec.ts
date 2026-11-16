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
import { deleteUser, listUserAuthorizationDetailConsents, listUserScopeConsents } from '@management-commands/user-management-commands';
import { deleteApplication } from '@management-commands/application-management-commands';
import { AUTHORIZATION_DETAILS_TEST, toDetailsParam } from '../../gateway/authorization-details/fixtures/authorization-details-fixture';
import {
  account,
  CONSENT_TEST,
  ConsentFixture,
  isConsentPage,
  payment,
  setupConsentFixture,
} from '../../gateway/authorization-details/fixtures/authorization-details-consent-fixture';

// Deleting a user or an application deletes the scope and authorization detail consents that belong to it

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
}, 60000);

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const authorizationDetailClientsOf = async (userId: string): Promise<string[]> =>
  (await listUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId)).data.map((consent) => consent.clientId);

const scopeClientsOf = async (userId: string): Promise<string[]> =>
  (await listUserScopeConsents(fixture.domain.id, fixture.accessToken, userId)).map((consent) => consent.clientId);

describe('Deleting the owner of consents in the Management API', () => {
  it(jira`should delete the consents for an application when the application is deleted ${'AM-8143'}`, async () => {
    const user = fixture.nextUser();
    const session = await fixture.approve(user, [payment()], { scope: CONSENT_TEST.SCOPE });
    const other = await fixture.createApplication([AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]);
    await other.approve(session, [account()], { scope: CONSENT_TEST.SCOPE });
    const userId = await fixture.userIdOf(user);
    expect((await authorizationDetailClientsOf(userId)).sort()).toEqual([fixture.clientId, other.clientId].sort());
    expect((await scopeClientsOf(userId)).sort()).toEqual([fixture.clientId, other.clientId].sort());

    await deleteApplication(fixture.domain.id, fixture.accessToken, other.application.id);

    expect(await authorizationDetailClientsOf(userId)).toEqual([fixture.clientId]);
    expect(await scopeClientsOf(userId)).toEqual([fixture.clientId]);
  });

  it(
    jira`should delete the consents of a user when the user is deleted, and ask them to consent after signing in again ${'AM-8143'}`,
    async () => {
      const deleted = fixture.nextUser();
      await fixture.approve(deleted, [payment()], { scope: CONSENT_TEST.SCOPE });
      const kept = fixture.nextUser();
      await fixture.approve(kept, [payment()], { scope: CONSENT_TEST.SCOPE });
      const keptId = await fixture.userIdOf(kept);

      await deleteUser(fixture.domain.id, fixture.accessToken, await fixture.userIdOf(deleted));

      // the identity provider recreates the user with the same external id
      const signedInAgain = await fixture.signIn(deleted, { scope: CONSENT_TEST.SCOPE, authorization_details: toDetailsParam(payment()) });
      expect(isConsentPage(signedInAgain)).toBe(true);
      const recreatedId = await fixture.userIdOf(deleted);
      expect(await authorizationDetailClientsOf(recreatedId)).toEqual([]);
      expect(await scopeClientsOf(recreatedId)).toEqual([]);
      expect(await authorizationDetailClientsOf(keptId)).toEqual([fixture.clientId]);
      expect(await scopeClientsOf(keptId)).toEqual([fixture.clientId]);
    },
  );
});
