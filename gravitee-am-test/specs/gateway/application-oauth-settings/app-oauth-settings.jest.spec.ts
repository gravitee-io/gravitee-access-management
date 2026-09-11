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
import { waitFor } from '@management-commands/domain-management-commands';
import { decodeJwt } from '@utils-commands/jwt';
import { setup } from '../../test-fixture';
import {
  APP_OAUTH_SETTINGS,
  AppOAuthSettingsFixture,
  authorize,
  consentPageUrl,
  expectAuthorizationCodeRedirect,
  requestPasswordToken,
  requestRefreshedToken,
  setScopeSettings,
  setupAppOAuthSettingsFixture,
  signInAndConsent,
} from './fixtures/app-oauth-settings-fixture';

setup(200000);

/**
 * Application > Settings > OAuth 2.0 / OIDC: the per-application settings that change what the
 * gateway issues — consent duration on a scope, the scope list itself, and refresh token validity.
 * Access/ID token validity and the refresh grant itself are covered by the token-claims and
 * refresh-token specs.
 */
const { CONSENT_SCOPE, TOGGLE_SCOPE, SHORT_CONSENT_SECONDS, LONG_CONSENT_SECONDS, REFRESH_VALIDITY_SECONDS } = APP_OAUTH_SETTINGS;
const PAST_SHORT_CONSENT_MS = (SHORT_CONSENT_SECONDS + 1) * 1000;
const PAST_REFRESH_VALIDITY_MS = (REFRESH_VALIDITY_SECONDS + 1) * 1000;

let fixture: AppOAuthSettingsFixture;

beforeAll(async () => {
  fixture = await setupAppOAuthSettingsFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('Application OAuth settings - consent duration on a scope', () => {
  it('should ask for consent again once the application-level approval duration has elapsed', async () => {
    const user = fixture.users[0];
    await setScopeSettings(fixture, fixture.consentApp, [
      { scope: CONSENT_SCOPE, defaultScope: true, scopeApproval: SHORT_CONSENT_SECONDS },
    ]);

    const { session, callback } = await signInAndConsent(fixture, fixture.consentApp, user, [CONSENT_SCOPE]);
    expectAuthorizationCodeRedirect(callback);

    // Control: while the approval is live the consent page is skipped
    const withinDuration = await authorize(fixture, fixture.consentApp, session);
    expectAuthorizationCodeRedirect(withinDuration);

    await waitFor(PAST_SHORT_CONSENT_MS);

    const afterDuration = await authorize(fixture, fixture.consentApp, session);
    expect(afterDuration.headers['location']).toContain(consentPageUrl(fixture));
  });

  it('should keep reusing consent after the approval duration is raised', async () => {
    const user = fixture.users[1];
    await setScopeSettings(fixture, fixture.consentApp, [
      { scope: CONSENT_SCOPE, defaultScope: true, scopeApproval: LONG_CONSENT_SECONDS },
    ]);

    const { session, callback } = await signInAndConsent(fixture, fixture.consentApp, user, [CONSENT_SCOPE]);
    expectAuthorizationCodeRedirect(callback);

    // Same wait that expires the short approval: with the long one the consent page must stay skipped
    await waitFor(PAST_SHORT_CONSENT_MS);

    const afterShortWait = await authorize(fixture, fixture.consentApp, session);
    expectAuthorizationCodeRedirect(afterShortWait);
  });
});

describe('Application OAuth settings - adding and removing a scope', () => {
  const user = () => fixture.users[0];

  it('should issue a token carrying a scope once it is added to the application', async () => {
    await setScopeSettings(fixture, fixture.scopeApp, [
      { scope: 'openid', defaultScope: true },
      { scope: TOGGLE_SCOPE, defaultScope: false },
    ]);

    const response = await requestPasswordToken(fixture, fixture.scopeApp, user(), TOGGLE_SCOPE).expect(200);
    expect(response.body.scope).toEqual(TOGGLE_SCOPE);
  });

  it('should reject the same scope request once it is removed from the application', async () => {
    await setScopeSettings(fixture, fixture.scopeApp, [{ scope: 'openid', defaultScope: true }]);

    const rejected = await requestPasswordToken(fixture, fixture.scopeApp, user(), TOGGLE_SCOPE).expect(400);
    expect(rejected.body).toEqual({ error: 'invalid_scope', error_description: `Invalid scope(s): ${TOGGLE_SCOPE}` });

    // Control: the remaining scope is still issued, so it is the removal that is enforced, not the app being broken
    const stillIssued = await requestPasswordToken(fixture, fixture.scopeApp, user()).expect(200);
    expect(stillIssued.body.scope).toEqual('openid');
  });
});

describe('Application OAuth settings - refresh token validity', () => {
  const user = () => fixture.users[1];

  it('should refresh while the refresh token is still within its validity', async () => {
    const issued = await requestPasswordToken(fixture, fixture.refreshApp, user()).expect(200);
    expect(issued.body.refresh_token).toEqual(expect.any(String));
    // The setting is specific to the refresh token: the access token keeps its own default
    const accessToken = decodeJwt(issued.body.access_token);
    expect(accessToken.exp - accessToken.iat).toEqual(APP_OAUTH_SETTINGS.DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS);
    const refreshToken = decodeJwt(issued.body.refresh_token);
    expect(refreshToken.exp - refreshToken.iat).toEqual(REFRESH_VALIDITY_SECONDS);

    const refreshed = await requestRefreshedToken(fixture, fixture.refreshApp, issued.body.refresh_token).expect(200);
    expect(refreshed.body.access_token).toEqual(expect.any(String));
    expect(refreshed.body.access_token).not.toEqual(issued.body.access_token);
  });

  it('should reject the refresh grant once the refresh token validity has elapsed', async () => {
    const issued = await requestPasswordToken(fixture, fixture.refreshApp, user()).expect(200);

    await waitFor(PAST_REFRESH_VALIDITY_MS);

    const rejected = await requestRefreshedToken(fixture, fixture.refreshApp, issued.body.refresh_token).expect(400);
    expect(rejected.body.error).toEqual('invalid_grant');
    // "expired" when the stored token is still there, "invalid" once the store's TTL purge has removed it
    expect(rejected.body.error_description).toMatch(/^Refresh token is (expired|invalid)$/);
  });
});
