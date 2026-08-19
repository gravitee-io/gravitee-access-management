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
import { setupRevocationFixture, waitPastOfflineVerification, RevocationFixture } from './fixtures/revocation-fixture';

setup(200000);

/**
 * SCIM-driven offboarding. When an identity provider pushes `active: false`, AM must invalidate
 * that user's tokens — the same outcome as an admin disabling them in the Console, but via a
 * different route: ProvisioningUserServiceImpl#updateUser calls RevokeTokenGatewayService's
 * deleteByUser directly, bypassing the REVOKE_TOKEN event bus that every other spec here exercises.
 *
 * As in the other admin-triggered specs, the user is re-activated before asserting so that a
 * surviving 401 can only mean the token left the store.
 */
let fixture: RevocationFixture;

beforeAll(async () => {
  fixture = await setupRevocationFixture({
    domainNamePrefix: 'revoke-scim-disable',
    enableTokenExchange: false,
    withScim: true,
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SCIM disable - token revocation', () => {
  it('should revoke the user tokens when SCIM sets the user inactive', async () => {
    const tokens = await fixture.obtainAuthorizationCodeTokens();
    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);

    const deactivate = await fixture.scim!.setUserActive(fixture.user.id, false);
    expect(deactivate.status).toBe(200);
    expect(deactivate.body.active).toBe(false);

    const reactivate = await fixture.scim!.setUserActive(fixture.user.id, true);
    expect(reactivate.status).toBe(200);
    expect(reactivate.body.active).toBe(true);

    await fixture.waitUntilTokenRejected(tokens.accessToken);

    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(401);
  });

  /**
   * Control for the test above: a SCIM update that does not deactivate the user writes to the same
   * record through the same code path, so if it left the token intact then the 401 above is
   * attributable to the deactivation rather than to the user simply having been modified.
   */
  it('should not revoke tokens when SCIM updates a user without deactivating them', async () => {
    const activeUser = await fixture.createAdditionalUser(1);
    const tokens = await fixture.obtainAuthorizationCodeTokensAs(activeUser);
    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);

    const update = await fixture.scim!.setUserActive(activeUser.id, true);
    expect(update.status).toBe(200);

    await waitPastOfflineVerification(tokens.accessToken);

    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);
  });
});
