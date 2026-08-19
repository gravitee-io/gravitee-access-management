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
import { deleteUser, resetUserPassword } from '@management-commands/user-management-commands';
import { setup } from '../../test-fixture';
import { setupRevocationFixture, RevocationFixture } from './fixtures/revocation-fixture';

setup(200000);

/**
 * Admin lifecycle actions that revoke tokens, separate from the enable/disable status changes in
 * revoke-on-user-status-change. Both emit BY_USER, from ManagementUserServiceImpl#resetPassword
 * and ManagementUserServiceImpl#delete.
 */
const NEW_PASSWORD = 'An0therP@ssw0rd!';

let fixture: RevocationFixture;

beforeAll(async () => {
  fixture = await setupRevocationFixture({
    domainNamePrefix: 'revoke-user-lifecycle',
    enableTokenExchange: false,
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Admin password reset', () => {
  it('should revoke the user tokens so they must log in again', async () => {
    const user = await fixture.createAdditionalUser(1);
    const tokens = await fixture.obtainAuthorizationCodeTokensAs(user);
    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);

    await resetUserPassword(fixture.domain.id, fixture.accessToken, user.id, NEW_PASSWORD);

    await fixture.waitUntilTokenRejected(tokens.accessToken);

    // The user is untouched otherwise — still present and enabled — so a 401 means the token was
    // removed from the store, not that the subject lookup failed.
    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(401);
  });
});

describe('User deletion', () => {
  /**
   * Asserted through introspection rather than userinfo on purpose. Userinfo resolves the subject
   * and would 401 for a deleted user whether or not revocation ran, so it cannot tell the two
   * apart. Introspection never loads the user — it answers purely from the token store — so
   * `active: false` here is direct evidence the token was revoked.
   *
   * This is the case that matters for resource servers doing introspection-based authorization:
   * without revocation they would keep accepting a deleted user's token until it expired.
   */
  it('should revoke the user tokens so introspection reports them inactive', async () => {
    const user = await fixture.createAdditionalUser(2);
    const tokens = await fixture.obtainAuthorizationCodeTokensAs(user);
    expect((await fixture.introspectToken(tokens.accessToken)).active).toBe(true);

    await deleteUser(fixture.domain.id, fixture.accessToken, user.id);

    await fixture.waitUntilTokenInactive(tokens.accessToken);

    expect((await fixture.introspectToken(tokens.accessToken)).active).toBe(false);
  });
});
