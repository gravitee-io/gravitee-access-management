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
import { updateUserStatus } from '@management-commands/user-management-commands';
import { setup } from '../../test-fixture';
import { setupRevocationFixture, waitPastOfflineVerification, RevocationFixture } from './fixtures/revocation-fixture';

setup(200000);

/**
 * Disabling a user emits a BY_USER revoke event (ManagementUserServiceImpl#updateStatus), which
 * must kill that user's tokens across *every* application in the domain — this is what separates
 * BY_USER from the BY_USER_AND_CLIENT path already covered by revoke-user-consents.
 *
 * Each test re-enables the user before asserting. Re-enabling removes any rejection that could
 * stem from the user's own state, so a token that is still refused past the offline-verification
 * window can only have been deleted from the token store. Without that step these assertions
 * would pass on a disabled-user check alone and prove nothing about revocation.
 */
let fixture: RevocationFixture;

beforeAll(async () => {
  fixture = await setupRevocationFixture({
    domainNamePrefix: 'revoke-user-status',
    enableTokenExchange: false,
    withSecondaryApplication: true,
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('User disable - scope across applications', () => {
  it('should revoke the user tokens issued to every application in the domain', async () => {
    const primaryTokens = await fixture.obtainAuthorizationCodeTokens();
    const secondaryTokens = await fixture.secondaryClient!.obtainAuthorizationCodeTokens();

    expect((await fixture.getUserInfo(primaryTokens.accessToken)).status).toBe(200);
    expect((await fixture.getUserInfo(secondaryTokens.accessToken)).status).toBe(200);

    await updateUserStatus(fixture.domain.id, fixture.accessToken, fixture.user.id, false);
    await updateUserStatus(fixture.domain.id, fixture.accessToken, fixture.user.id, true);

    await fixture.waitUntilTokenRejected(primaryTokens.accessToken);
    await fixture.waitUntilTokenRejected(secondaryTokens.accessToken);

    expect((await fixture.getUserInfo(primaryTokens.accessToken)).status).toBe(401);
    expect((await fixture.getUserInfo(secondaryTokens.accessToken)).status).toBe(401);
  });

  it('should let the user obtain a working token again once re-enabled', async () => {
    const user = await fixture.createAdditionalUser(4);
    const tokensBeforeDisable = await fixture.obtainAuthorizationCodeTokensAs(user);
    expect((await fixture.getUserInfo(tokensBeforeDisable.accessToken)).status).toBe(200);

    await updateUserStatus(fixture.domain.id, fixture.accessToken, user.id, false);
    await fixture.waitUntilTokenRejected(tokensBeforeDisable.accessToken);

    await updateUserStatus(fixture.domain.id, fixture.accessToken, user.id, true);

    const tokensAfterEnable = await fixture.obtainAuthorizationCodeTokensAs(user);
    await waitPastOfflineVerification(tokensAfterEnable.accessToken);

    expect((await fixture.getUserInfo(tokensAfterEnable.accessToken)).status).toBe(200);
  });
});

describe('User disable - scope across users', () => {
  it('should leave another user tokens untouched', async () => {
    const disabledUser = await fixture.createAdditionalUser(1);
    const untouchedUser = await fixture.createAdditionalUser(2);

    const disabledUserTokens = await fixture.obtainAuthorizationCodeTokensAs(disabledUser);
    const untouchedUserTokens = await fixture.obtainAuthorizationCodeTokensAs(untouchedUser);

    expect((await fixture.getUserInfo(disabledUserTokens.accessToken)).status).toBe(200);
    expect((await fixture.getUserInfo(untouchedUserTokens.accessToken)).status).toBe(200);

    await updateUserStatus(fixture.domain.id, fixture.accessToken, disabledUser.id, false);
    await updateUserStatus(fixture.domain.id, fixture.accessToken, disabledUser.id, true);

    await fixture.waitUntilTokenRejected(disabledUserTokens.accessToken);
    // The surviving token must clear the offline-verification window before it proves anything.
    await waitPastOfflineVerification(untouchedUserTokens.accessToken);

    expect((await fixture.getUserInfo(disabledUserTokens.accessToken)).status).toBe(401);
    expect((await fixture.getUserInfo(untouchedUserTokens.accessToken)).status).toBe(200);
  });
});

describe('User enable', () => {
  /**
   * Doubles as the control for the disable tests above. This also writes to the user record, yet
   * the token survives past the offline-verification window — so those 401s cannot be explained by
   * "the user was updated", only by the revoke event that the disable branch alone emits.
   */
  it('should not revoke tokens when a user is enabled', async () => {
    const enabledUser = await fixture.createAdditionalUser(3);
    const tokens = await fixture.obtainAuthorizationCodeTokensAs(enabledUser);

    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);

    await updateUserStatus(fixture.domain.id, fixture.accessToken, enabledUser.id, true);

    await waitPastOfflineVerification(tokens.accessToken);

    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);
  });
});
