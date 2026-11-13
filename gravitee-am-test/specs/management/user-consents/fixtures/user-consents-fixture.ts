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
import { expect } from '@jest/globals';
import {
  listUserAuthorizationDetailConsents,
  ListUserAuthorizationDetailConsentsParams,
  listUsers,
} from '@management-commands/user-management-commands';
import { AuditEntry, auditsOfType } from '@management-commands/audit-management-commands';
import { AuthorizationDetailApprovalEntity } from '@management-models/AuthorizationDetailApprovalEntity';
import { retryUntil } from '@utils-commands/retry';
import { toDetailsParam } from '../../../gateway/authorization-details/fixtures/authorization-details-fixture';
import {
  ConsentFixture,
  expectError,
  follow,
  openConsentPage,
  setupConsentFixture,
  submitConsent,
  TestUser,
} from '../../../gateway/authorization-details/fixtures/authorization-details-consent-fixture';

export interface UserConsentsFixture extends ConsentFixture {
  userIdOf: (user: TestUser) => Promise<string>;
  consentsOf: (userId: string, params?: ListUserAuthorizationDetailConsentsParams) => Promise<AuthorizationDetailApprovalEntity[]>;
  /** Approves `approved`, exchanges the code, then rejects `denied` in the same session; returns the access token. */
  approveThenDeny: (user: TestUser, approved: unknown[], denied: unknown[]) => Promise<string>;
  expectInactive: (token: string) => Promise<unknown>;
  revocationAuditsOf: (userId: string) => Promise<AuditEntry[]>;
}

export const setupUserConsentsFixture = async (): Promise<UserConsentsFixture> => {
  const fixture = await setupConsentFixture();

  const userIdOf = async (user: TestUser): Promise<string> => {
    const page = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
    expect(page.data).toHaveLength(1);
    return page.data[0].id;
  };

  const consentsOf = async (
    userId: string,
    params: ListUserAuthorizationDetailConsentsParams = {},
  ): Promise<AuthorizationDetailApprovalEntity[]> =>
    (await listUserAuthorizationDetailConsents(fixture.domain.id, fixture.accessToken, userId, params)).data;

  const approveThenDeny = async (user: TestUser, approved: unknown[], denied: unknown[]): Promise<string> => {
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
      async () =>
        (await auditsOfType(fixture.domain.id, fixture.accessToken, 'USER_CONSENT_REVOKED')).filter((audit) => audit.target?.id === userId),
      (audits) => audits.length > 0,
      { timeoutMillis: 30000, intervalMillis: 500 },
    );

  return { ...fixture, userIdOf, consentsOf, approveThenDeny, expectInactive, revocationAuditsOf };
};
