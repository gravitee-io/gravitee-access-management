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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { setupRevocationFixture, waitPastOfflineVerification, RevocationFixture } from './fixtures/revocation-fixture';

setup(200000);

/**
 * RFC 7009 client-initiated revocation via POST /oauth/revoke (AM-6865).
 *
 * Unlike the admin-triggered specs in this folder, nothing here changes the state of the client
 * or the user — so once past the offline-verification window, a rejected token can only have been
 * removed from the token store. No re-enable step is needed to make these assertions meaningful.
 *
 * Note the window itself is not asserted: a token stays usable for up to
 * `handlers.oauth2.introspect.offlineVerificationTimerSeconds` (default 10s) after revocation, and
 * pinning that would mean asserting a response inside a race against wall-clock time. The tests
 * below therefore always wait past it and assert the settled outcome.
 */
let fixture: RevocationFixture;

beforeAll(async () => {
  fixture = await setupRevocationFixture({
    domainNamePrefix: 'revoke-token-endpoint',
    enableTokenExchange: false,
    withSecondaryApplication: true,
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Revocation endpoint - access token', () => {
  it('should reject a revoked access token at the userinfo endpoint', async () => {
    const tokens = await fixture.obtainAuthorizationCodeTokens();
    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);

    const revocation = await fixture.revokeToken(tokens.accessToken);
    expect(revocation.status).toBe(200);

    await waitPastOfflineVerification(tokens.accessToken);

    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(401);
  });

  it('should report a revoked access token as inactive at the introspection endpoint', async () => {
    const tokens = await fixture.obtainAuthorizationCodeTokens();
    expect((await fixture.introspectToken(tokens.accessToken)).active).toBe(true);

    const revocation = await fixture.revokeToken(tokens.accessToken);
    expect(revocation.status).toBe(200);

    await waitPastOfflineVerification(tokens.accessToken);

    expect((await fixture.introspectToken(tokens.accessToken)).active).toBe(false);
  });

  /**
   * Characterisation test, not an endorsement. RFC 7009 §2.1 says the authorization server SHOULD
   * revoke every token issued from the same grant; AM deletes only the presented JTI plus any
   * token-exchange children (MongoTokenRepository#deleteByJtis matches jti or parentJtis), so the
   * sibling refresh token survives and can still mint a new access token. Raised with the team —
   * if this is changed deliberately, this test should be updated to match.
   */
  it('should leave the sibling refresh token usable after the access token is revoked', async () => {
    const tokens = await fixture.obtainAuthorizationCodeTokens();

    const revocation = await fixture.revokeToken(tokens.accessToken);
    expect(revocation.status).toBe(200);

    await waitPastOfflineVerification(tokens.accessToken);
    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(401);

    const refreshed = await performPost(fixture.oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${tokens.refreshToken}`, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${fixture.basicAuth}`,
    });

    expect(refreshed.status).toBe(200);
    expect((await fixture.getUserInfo(refreshed.body.access_token)).status).toBe(200);
  });
});

describe('Revocation endpoint - refresh token', () => {
  it('should revoke the refresh token when token_type_hint is refresh_token', async () => {
    const tokens = await fixture.obtainAuthorizationCodeTokens();

    const revocation = await fixture.revokeToken(tokens.refreshToken!, { tokenTypeHint: 'refresh_token' });
    expect(revocation.status).toBe(200);

    const refreshed = await performPost(fixture.oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${tokens.refreshToken}`, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${fixture.basicAuth}`,
    });

    expect(refreshed.status).toBe(400);
    expect(refreshed.body.error).toBe('invalid_grant');
  });
});

describe('Revocation endpoint - client scoping', () => {
  it('should refuse to revoke a token issued to another client and leave that token valid', async () => {
    const tokens = await fixture.obtainAuthorizationCodeTokens();

    const revocation = await fixture.revokeToken(tokens.accessToken, {
      basicAuth: fixture.secondaryClient!.basicAuth,
    });

    expect(revocation.status).toBe(400);
    expect(revocation.body.error).toBe('invalid_grant');

    // The refusal must be a no-op, not a partial revocation.
    await waitPastOfflineVerification(tokens.accessToken);
    expect((await fixture.getUserInfo(tokens.accessToken)).status).toBe(200);
  });
});

describe('Revocation endpoint - unknown tokens', () => {
  it('should return 200 for a token it cannot find', async () => {
    const revocation = await fixture.revokeToken('not-a-token-issued-by-this-server');

    expect(revocation.status).toBe(200);
  });
});
