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
import { patchApplication } from '@management-commands/application-management-commands';
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { withRetry } from '@utils-commands/retry';
import { setup } from '../../test-fixture';
import { setupRevocationFixture, waitPastOfflineVerification, RevocationFixture, SubjectTokens } from './fixtures/revocation-fixture';

setup(200000);

/**
 * Disabling an application has two independent effects (AM-4044):
 *   1. the gateway undeploys the client, so it can no longer obtain new tokens;
 *   2. the management API emits a BY_CLIENT revoke event, so tokens already issued die.
 *
 * Both are asserted here, along with the blast radius: another application in the same
 * domain must be unaffected.
 *
 * Assertions that a token is now rejected poll (`waitUntilTokenRejected`). Assertions that a token
 * is still valid first clear the offline-verification window (`waitPastOfflineVerification`),
 * without which the gateway may still be trusting the JWT and the assertion would hold even for a
 * revoked token. See the fixture for how that window is configured.
 */
let fixture: RevocationFixture;
let disabledAppTokens: SubjectTokens;
let otherAppTokens: SubjectTokens;

beforeAll(async () => {
  fixture = await setupRevocationFixture({
    domainNamePrefix: 'revoke-app-disable',
    enableTokenExchange: false,
    withSecondaryApplication: true,
  });

  disabledAppTokens = await fixture.obtainAuthorizationCodeTokens();
  otherAppTokens = await fixture.secondaryClient!.obtainAuthorizationCodeTokens();

  // Control: both tokens are accepted while both applications are enabled.
  expect((await fixture.getUserInfo(disabledAppTokens.accessToken)).status).toBe(200);
  expect((await fixture.getUserInfo(otherAppTokens.accessToken)).status).toBe(200);

  // Not wrapped in waitForSyncAfter: disabling an application does not reliably advance the
  // domain's `lastSync`, so that helper can wait 90s for a signal that never arrives. Every
  // assertion below polls for the outcome instead. See the comment on the re-enable test.
  await patchApplication(fixture.domain.id, fixture.accessToken, { enabled: false }, fixture.application.id);
  await waitForOidcReady(fixture.domain.hrid, { timeoutMs: 5000, intervalMs: 200 });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Application disable - tokens already issued', () => {
  /**
   * Note this passes on the undeploy effect alone: with the client removed from the gateway,
   * the token's `aud` no longer resolves and introspection fails before the token store is
   * ever consulted. It asserts the user-visible outcome, not that revocation ran — that is
   * what the 'survives the application being re-enabled' test below is for.
   */
  it('should reject the access token at the userinfo endpoint', async () => {
    await fixture.waitUntilTokenRejected(disabledAppTokens.accessToken);

    expect((await fixture.getUserInfo(disabledAppTokens.accessToken)).status).toBe(401);
  });

  it('should refuse to mint a new access token from the refresh token', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=refresh_token&refresh_token=${disabledAppTokens.refreshToken}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${fixture.basicAuth}`,
      },
    );

    expect(response.status).toBe(401);
    expect(response.body.error).toBe('invalid_client');
  });
});

describe('Application disable - new token requests', () => {
  it('should reject the authorization_code grant for the disabled client', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      'grant_type=authorization_code&code=any-code&redirect_uri=https%3A%2F%2Fgravitee.io%2Fcallback',
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${fixture.basicAuth}`,
      },
    );

    expect(response.status).toBe(401);
    expect(response.body.error).toBe('invalid_client');
  });
});

describe('Application disable - tokens are revoked, not just unresolvable', () => {
  /**
   * Discriminates real BY_CLIENT revocation from the undeploy side effect. Re-enabling the
   * application redeploys the client, so the token's audience resolves again and introspection
   * reaches the token-store lookup. Past the offline-verification window, the only thing that
   * can still reject the old token is its absence from the store — i.e. it really was revoked.
   */
  it('should keep rejecting a pre-disable access token after the application is re-enabled', async () => {
    const client = await fixture.createAdditionalClient('revoke-cycle-client');
    const preDisableTokens = await client.obtainAuthorizationCodeTokens();
    expect((await fixture.getUserInfo(preDisableTokens.accessToken)).status).toBe(200);

    // waitForSyncAfter is not usable around these: an application enable/disable does not reliably
    // advance the domain's `lastSync`, so it waits 90s for a signal that never arrives (measured —
    // lastSync held steady for 24s after a disable while the domain reported stable + synchronized).
    // Sequence on the observable outcome instead. The wait between disable and enable is load
    // bearing: without it the BY_CLIENT revoke event can land after the new token is minted and
    // revoke that one too.
    await patchApplication(fixture.domain.id, fixture.accessToken, { enabled: false }, client.application.id);
    await fixture.waitUntilTokenRejected(preDisableTokens.accessToken);

    await patchApplication(fixture.domain.id, fixture.accessToken, { enabled: true }, client.application.id);
    await waitForOidcReady(fixture.domain.hrid, { timeoutMs: 5000, intervalMs: 200 });

    // The client is usable again — proves any 401 below is about the token, not the application.
    const postEnableTokens = await withRetry(() => client.obtainAuthorizationCodeTokens(), 40, 500);
    expect((await fixture.getUserInfo(postEnableTokens.accessToken)).status).toBe(200);

    // The surviving token must clear the offline-verification window, otherwise the gateway short
    // circuits on the JWT alone and this assertion would hold even if it had been revoked.
    await waitPastOfflineVerification(postEnableTokens.accessToken);

    // Same client, same audience, same signing certificate — so the only difference the gateway
    // can act on is whether the token is still in the store.
    expect((await fixture.getUserInfo(preDisableTokens.accessToken)).status).toBe(401);
    expect((await fixture.getUserInfo(postEnableTokens.accessToken)).status).toBe(200);
  });
});

describe('Application disable - blast radius', () => {
  it('should keep the access token issued to another application in the domain valid', async () => {
    // Without this the gateway may still be trusting the JWT, so the assertion would hold even if
    // disabling the first application had wrongly revoked this token too.
    await waitPastOfflineVerification(otherAppTokens.accessToken);

    const response = await fixture.getUserInfo(otherAppTokens.accessToken);

    expect(response.status).toBe(200);
    expect(response.body.sub).toEqual(expect.any(String));
  });

  it('should still issue tokens to another application in the domain', async () => {
    const tokens = await fixture.secondaryClient!.obtainAuthorizationCodeTokens();

    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);
  });
});
