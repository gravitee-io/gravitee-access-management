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
import { listUserConsents, revokeUserConsent, revokeUserConsents } from '@management-commands/user-management-commands';
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../../test-fixture';
import { setupRevocationFixture, RevocationFixture } from './fixtures/revocation-fixture';

setup(200000);

let fixture: RevocationFixture;

beforeAll(async () => {
  fixture = await setupRevocationFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Revoke user consents', () => {
  it('should invalidate subject and exchanged access tokens after revokeUserConsents', async () => {
    const { obtainAuthorizationCodeTokens, exchangeToken, introspectToken, application, domain, user, accessToken } = fixture;

    const authorizationCodeTokens = await obtainAuthorizationCodeTokens();
    const subjectAccessToken = authorizationCodeTokens.accessToken;
    const exchangedToken = await exchangeToken(subjectAccessToken, 'access_token');

    expect((await introspectToken(subjectAccessToken)).active).toBe(true);
    expect((await introspectToken(exchangedToken)).active).toBe(true);

    await revokeUserConsents(domain.id, accessToken, user.id, application.settings.oauth.clientId);

    await retryUntil(
      () => introspectToken(subjectAccessToken).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      {
        timeoutMillis: 15000,
        intervalMillis: 300,
      },
    );

    await retryUntil(
      () => introspectToken(exchangedToken).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      {
        timeoutMillis: 15000,
        intervalMillis: 300,
      },
    );
  });

  it('should invalidate subject refresh token and exchanged access token after revokeUserConsents', async () => {
    const { obtainAuthorizationCodeTokens, exchangeToken, introspectToken, application, domain, user, accessToken } = fixture;

    const authorizationCodeTokens = await obtainAuthorizationCodeTokens();
    const subjectRefreshToken = authorizationCodeTokens.refreshToken;
    expect(subjectRefreshToken).toBeDefined();

    const exchangedToken = await exchangeToken(subjectRefreshToken!, 'refresh_token');

    expect((await introspectToken(subjectRefreshToken!)).active).toBe(true);
    expect((await introspectToken(exchangedToken)).active).toBe(true);

    await revokeUserConsents(domain.id, accessToken, user.id, application.settings.oauth.clientId);

    await retryUntil(
      () => introspectToken(subjectRefreshToken!).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      {
        timeoutMillis: 15000,
        intervalMillis: 300,
      },
    );

    await retryUntil(
      () => introspectToken(exchangedToken).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      {
        timeoutMillis: 15000,
        intervalMillis: 300,
      },
    );
  });

  it('should invalidate refresh token issued by authorization_code after revokeUserConsents', async () => {
    const { obtainAuthorizationCodeTokens, introspectToken, application, domain, user, accessToken } = fixture;

    const authorizationCodeTokens = await obtainAuthorizationCodeTokens();
    const refreshToken = authorizationCodeTokens.refreshToken;
    expect(refreshToken).toBeDefined();
    expect((await introspectToken(refreshToken!)).active).toBe(true);

    await revokeUserConsents(domain.id, accessToken, user.id, application.settings.oauth.clientId);

    await retryUntil(
      () => introspectToken(refreshToken!).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      {
        timeoutMillis: 15000,
        intervalMillis: 300,
      },
    );
  });

  it('should invalidate access token issued by authorization_code after revokeUserConsents', async () => {
    const { obtainAuthorizationCodeTokens, introspectToken, application, domain, user, accessToken } = fixture;

    const authorizationCodeTokens = await obtainAuthorizationCodeTokens();
    const issuedAccessToken = authorizationCodeTokens.accessToken;
    expect(issuedAccessToken).toBeDefined();

    expect((await introspectToken(issuedAccessToken)).active).toBe(true);

    await revokeUserConsents(domain.id, accessToken, user.id, application.settings.oauth.clientId);

    await retryUntil(
      () => introspectToken(issuedAccessToken).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      {
        timeoutMillis: 15000,
        intervalMillis: 300,
      },
    );
  });

  it('should invalidate access token issued by authorization_code after revokeUserConsent by id', async () => {
    const { obtainAuthorizationCodeTokens, introspectToken, application, domain, user, accessToken } = fixture;

    const authorizationCodeTokens = await obtainAuthorizationCodeTokens();
    const issuedAccessToken = authorizationCodeTokens.accessToken;
    expect(issuedAccessToken).toBeDefined();
    expect((await introspectToken(issuedAccessToken)).active).toBe(true);

    const consents = await listUserConsents(domain.id, accessToken, user.id, application.settings.oauth.clientId);
    expect(consents).toBeDefined();
    expect(consents.length).toBeGreaterThan(0);

    const consentId = consents[0]?.id;
    expect(consentId).toBeDefined();

    await revokeUserConsent(domain.id, accessToken, user.id, consentId!);

    await retryUntil(
      () => introspectToken(issuedAccessToken).then((body) => ({ active: body.active })),
      (response) => response.active === false,
      {
        timeoutMillis: 15000,
        intervalMillis: 300,
      },
    );
  });
});
