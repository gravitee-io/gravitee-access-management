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
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';
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
 * Revocation is not immediate — `handlers.oauth2.introspect.offlineVerificationTimerSeconds`
 * (default 10s) lets the gateway skip the token-store lookup for freshly issued tokens.
 * Post-disable assertions therefore poll rather than checking once.
 */
const REVOCATION_TIMEOUT_MS = 30000;

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

  await waitForSyncAfter(fixture.domain.id, () =>
    patchApplication(fixture.domain.id, fixture.accessToken, { enabled: false }, fixture.application.id),
  );
  // Sync completion does not imply the gateway has finished rebuilding its routes (GUIDELINES §3).
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
    const status = await retryUntil(
      () => fixture.getUserInfo(disabledAppTokens.accessToken).then((response) => response.status),
      (responseStatus) => responseStatus === 401,
      { timeoutMillis: REVOCATION_TIMEOUT_MS, intervalMillis: 500 },
    );

    expect(status).toBe(401);
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

    await waitForSyncAfter(fixture.domain.id, () =>
      patchApplication(fixture.domain.id, fixture.accessToken, { enabled: false }, client.application.id),
    );
    await waitForSyncAfter(fixture.domain.id, () =>
      patchApplication(fixture.domain.id, fixture.accessToken, { enabled: true }, client.application.id),
    );
    await waitForOidcReady(fixture.domain.hrid, { timeoutMs: 5000, intervalMs: 200 });

    // The client is usable again — proves any 401 below is about the token, not the application.
    const postEnableTokens = await client.obtainAuthorizationCodeTokens();
    expect((await fixture.getUserInfo(postEnableTokens.accessToken)).status).toBe(200);

    // Both tokens must be past their offline-verification window, otherwise the gateway short
    // circuits on the JWT alone and never looks either of them up in the token store.
    await waitPastOfflineVerification(preDisableTokens.accessToken);
    await waitPastOfflineVerification(postEnableTokens.accessToken);

    // Same client, same audience, same signing certificate — so the only difference the gateway
    // can act on is whether the token is still in the store.
    expect((await fixture.getUserInfo(preDisableTokens.accessToken)).status).toBe(401);
    expect((await fixture.getUserInfo(postEnableTokens.accessToken)).status).toBe(200);
  });
});

describe('Application disable - blast radius', () => {
  it('should keep the access token issued to another application in the domain valid', async () => {
    const response = await fixture.getUserInfo(otherAppTokens.accessToken);

    expect(response.status).toBe(200);
    expect(response.body.sub).toEqual(expect.any(String));
  });

  it('should still issue tokens to another application in the domain', async () => {
    const tokens = await fixture.secondaryClient!.obtainAuthorizationCodeTokens();

    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);
  });
});
