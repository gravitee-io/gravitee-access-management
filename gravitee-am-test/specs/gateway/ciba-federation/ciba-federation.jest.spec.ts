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
import { getAllUsers } from '@management-commands/user-management-commands';
import { CibaFederationFixture, setupCibaFederationFixture } from './fixtures/ciba-federation-fixture';
import { TARGET_USER } from './fixtures/target-domain-fixture';
import { JWT_FORMAT } from '../../utils/jwt-format';

setup(300000);

const NON_EMPTY_ID = expect.stringMatching(/^.+$/);

let fixture: CibaFederationFixture;

// How this works:
// 1. Two domains — "hint" (domainA) receives the client's bc-authorize call with a login_hint that only
//    the "target" domain (domainB) can resolve. domainA's device notifier is the CIBA-federation notifier,
//    configured with an OIDC identity provider that points at domainB's discovery document.
// 2. The federation notifier relays bc-authorize to domainB, which notifies its own local user via the
//    HTTP device notifier (pointed at the CIBA delegated-service mock's accept-all/reject-all endpoints),
//    then polls domainB's token endpoint and calls back domainA to finalize the transaction.
beforeAll(async () => {
  fixture = await setupCibaFederationFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('CIBA Federation — happy path', () => {
  it('issues an access_token and id_token from the hint domain after the target domain user accepts', async () => {
    const init = await fixture.hintDomain.initiateCiba(TARGET_USER.username);
    expect(init.status).toBe(200);
    expect(init.body).toMatchObject({ auth_req_id: NON_EMPTY_ID });

    const token = await fixture.hintDomain.pollTokenUntilGranted(init.body.auth_req_id);
    expect(token.status).toBe(200);
    expect(token.body.access_token).toEqual(expect.any(String));
    expect(token.body.id_token).toMatch(JWT_FORMAT);
  });

  it('materializes the federated user in the hint domain user list although it never logged in there directly', async () => {
    // The federated user never logs in to the hint domain directly — the CIBA callback provisions it locally from
    // the target domain's token/userinfo response, identified by source/identities rather than by username (the
    // target domain's `sub` claim becomes the local username since only the `openid` scope was requested).
    const users = await getAllUsers(fixture.hintDomain.domain.id, fixture.accessToken);
    const federatedUser = users.data.find((u) => u.source === fixture.hintDomain.federationIdp.name);
    expect(federatedUser).toBeDefined();
    expect(federatedUser.internal).toBe(false);
  });
});

describe('CIBA Federation — access denied path', () => {
  it('returns access_denied when the target domain user rejects the notification', async () => {
    await fixture.targetDomain.switchNotifier('reject-all');
    try {
      const init = await fixture.hintDomain.initiateCiba(TARGET_USER.username);
      expect(init.status).toBe(200);
      expect(init.body).toMatchObject({ auth_req_id: NON_EMPTY_ID });

      await expect(fixture.hintDomain.pollTokenUntilGranted(init.body.auth_req_id)).rejects.toThrow(/access_denied/);
    } finally {
      await fixture.targetDomain.switchNotifier('accept-all');
    }
  });
});
