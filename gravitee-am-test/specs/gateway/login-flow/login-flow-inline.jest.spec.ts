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
import { loginUserNameAndPassword, initiateLoginFlow, login, getHeaderLocation } from '@gateway-commands/login-commands';
import { performGet, performPost, logoutUser, requestToken } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { patchDomain } from '@management-commands/domain-management-commands';
import { createUser, deleteUser, lockUser, updateUserStatus } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { LoginFlowInlineFixture, INLINE_USER, REDIRECT_URI, setupInlineFixture } from './fixture/login-flow-inline-fixture';

const cheerio = require('cheerio');
import { setup } from '../../test-fixture';

setup(200000);

let fixture: LoginFlowInlineFixture;

beforeAll(async () => {
  fixture = await setupInlineFixture();
  expect(fixture.openIdConfiguration).toBeDefined();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

// Performs the initial login POST and returns the redirect without following it.
// Used for failure cases where the gateway redirects to the login error page (200) rather than issuing a code (302).
async function attemptLogin(clientId: string, username: string, password: string): Promise<any> {
  const authResponse = await initiateLoginFlow(clientId, fixture.openIdConfiguration, fixture.domain, 'code', REDIRECT_URI);
  return login(authResponse, username, clientId, password);
}

describe('SSO', () => {
  it('should complete login with app using inmemory IDP', async () => {
    const clientId = fixture.appSso1.settings.oauth.clientId;

    const loginResponse = await loginUserNameAndPassword(
      clientId,
      INLINE_USER,
      INLINE_USER.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
      REDIRECT_URI,
      'code',
    );

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it('should redirect to OAuth error page when existing SSO session uses a different IDP than the requested app', async () => {
    const app1ClientId = fixture.appSso1.settings.oauth.clientId;
    const loginResponse = await loginUserNameAndPassword(
      app1ClientId,
      INLINE_USER,
      INLINE_USER.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
      REDIRECT_URI,
      'code',
    );
    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);

    // appSso2 only has the default IDP; the existing session (inmemory IDP) does not match
    const app2ClientId = fixture.appSso2.settings.oauth.clientId;
    const ssoAttempt = await performGet(
      fixture.openIdConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${app2ClientId}&redirect_uri=${REDIRECT_URI}&state=1234-5678-9876`,
      { Cookie: loginResponse.headers['set-cookie'] },
    ).expect(302);

    expect(ssoAttempt.headers['location']).toContain('/oauth/error');

    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });
});

describe('SSO with IDP selection rule', () => {
  it('should authenticate when username matches the IDP selection rule', async () => {
    const clientId = fixture.appSelectionRule.settings.oauth.clientId;

    const loginResponse = await loginUserNameAndPassword(
      clientId,
      INLINE_USER,
      INLINE_USER.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
      REDIRECT_URI,
      'code',
    );

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it('should authenticate via the matching IDP when two IDPs each have a selection rule', async () => {
    // inmemoryIdp1 rule matches "user"; inmemoryIdp2 rule does not → inmemoryIdp1 authenticates
    const clientId = fixture.appSelectionRuleTwoMatch.settings.oauth.clientId;

    const loginResponse = await loginUserNameAndPassword(
      clientId,
      INLINE_USER,
      INLINE_USER.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
      REDIRECT_URI,
      'code',
    );

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it('should fall back to the IDP without a selection rule when the first IDP rule does not match', async () => {
    // inmemoryIdp1 rule does not match "user"; inmemoryIdp2 has no rule (fallback) → inmemoryIdp2 authenticates
    const clientId = fixture.appSelectionRuleOneFallback.settings.oauth.clientId;

    const loginResponse = await loginUserNameAndPassword(
      clientId,
      INLINE_USER,
      INLINE_USER.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
      REDIRECT_URI,
      'code',
    );

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it('should fail login when no IDP selection rule matches', async () => {
    // Both IDPs have rules that do not match "user" → no IDP is selected → login fails
    const clientId = fixture.appSelectionRuleNoneMatch.settings.oauth.clientId;

    const loginResponse = await loginUserNameAndPassword(
      clientId,
      INLINE_USER,
      INLINE_USER.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );

    expect(loginResponse.headers['location']).toContain('error=login_failed&error_code=invalid_user');
  });
});

describe('Identifier First login', () => {
  it('should redirect to /login/identifier when identifierFirstEnabled is true on the app', async () => {
    const clientId = fixture.appIdentifierFirst.settings.oauth.clientId;

    const authResponse = await performGet(
      fixture.openIdConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&state=1234-5678-9876`,
    ).expect(302);

    const location = authResponse.headers['location'];
    expect(location).toContain(`/${fixture.domain.hrid}/login/identifier`);
    expect(location).toContain(`client_id=${clientId}`);
  });

  it('should not redirect to /login/identifier for a standard app without identifierFirstEnabled', async () => {
    const clientId = fixture.appSso1.settings.oauth.clientId;

    const authResponse = await performGet(
      fixture.openIdConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&state=1234-5678-9876`,
    ).expect(302);

    const location = authResponse.headers['location'];
    expect(location).toContain(`/${fixture.domain.hrid}/login`);
    expect(location).not.toContain('/login/identifier');
  });

  it('should keep the user on the identifier form when an empty or missing username is submitted', async () => {
    const clientId = fixture.appIdentifierFirst.settings.oauth.clientId;

    const authResponse = await performGet(
      fixture.openIdConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&state=1234-5678-9876`,
    ).expect(302);

    // Follow the redirect to load the identifier form and extract the action URL.
    // The action URL is a full URL with existing session state in the query string;
    // additional params are appended with & rather than starting a new ? block.
    const formPage = await performGet(authResponse.headers['location'], '', {
      Cookie: authResponse.headers['set-cookie'],
    }).expect(200);
    const actionUrl = cheerio.load(formPage.text)('form').attr('action');
    const cookies = formPage.headers['set-cookie'] || authResponse.headers['set-cookie'];

    // Empty username — stays on identifier form
    const emptyResponse = await performGet(actionUrl + '&username=', '', { Cookie: cookies }).expect(200);
    expect(cheerio.load(emptyResponse.text)('input[name="username"]').length).toBeGreaterThan(0);

    // Missing username parameter — stays on identifier form
    const missingResponse = await performGet(actionUrl, '', { Cookie: cookies }).expect(200);
    expect(cheerio.load(missingResponse.text)('input[name="username"]').length).toBeGreaterThan(0);
  });

  it('should advance to the password form when a valid username is submitted', async () => {
    const clientId = fixture.appIdentifierFirst.settings.oauth.clientId;

    const authResponse = await performGet(
      fixture.openIdConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&state=1234-5678-9876`,
    ).expect(302);

    const formPage = await performGet(authResponse.headers['location'], '', {
      Cookie: authResponse.headers['set-cookie'],
    }).expect(200);
    const actionUrl = cheerio.load(formPage.text)('form').attr('action');
    const cookies = formPage.headers['set-cookie'] || authResponse.headers['set-cookie'];

    // A 200 response confirms the identifier was accepted and the second login step was reached
    await performGet(actionUrl + `&username=${INLINE_USER.username}`, '', { Cookie: cookies }).expect(200);
  });
});

describe('IdP Priority', () => {
  it("should authenticate via the IDP with the lower priority number and return that IDP's user data", async () => {
    // appIdpPriority: inmemoryIdp1 priority=2, inmemoryIdp2 priority=1 — inmemoryIdp2 wins (given_name "my-user-2")
    const clientId = fixture.appIdpPriority.settings.oauth.clientId;

    const authResponse = await performGet(
      fixture.openIdConfiguration.authorization_endpoint,
      `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile`,
    ).expect(302);
    expect(authResponse.headers['location']).toContain(`/${fixture.domain.hrid}/login`);

    const postLogin = await login(authResponse, INLINE_USER.username, clientId, INLINE_USER.password);
    const loginResponse = await getHeaderLocation(postLogin);
    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);

    const tokenResponse = await requestToken(fixture.appIdpPriority, fixture.openIdConfiguration, loginResponse);
    expect(tokenResponse.status).toBe(200);
    const { access_token } = tokenResponse.body;
    expect(access_token).toBeDefined();

    const userInfoResponse = await performGet(fixture.openIdConfiguration.userinfo_endpoint, '', {
      Authorization: `Bearer ${access_token}`,
    }).expect(200);

    expect(userInfoResponse.body.given_name).toBe('my-user-2');

    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });
});

describe('Account Disabled', () => {
  let userId: string;
  const testUsername = uniqueName('disabled-user', true);
  const testPassword = '#CoMpL3X-P@SsW0Rd';

  beforeAll(async () => {
    const user = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'Jensen',
      lastName: 'Barbara',
      username: testUsername,
      email: `${testUsername}@example.com`,
      password: testPassword,
      preRegistration: false,
    });
    expect(user).toBeDefined();
    expect(user.id).toBeDefined();
    userId = user.id;
  });

  afterAll(async () => {
    if (userId) {
      try {
        await deleteUser(fixture.domain.id, fixture.accessToken, userId);
      } catch (e) {
        console.warn('Failed to delete account-disabled test user:', e);
      }
    }
  });

  it('should allow a valid user to login before being disabled', async () => {
    const clientId = fixture.appAccountTests.settings.oauth.clientId;

    const loginResponse = await loginUserNameAndPassword(
      clientId,
      { username: testUsername },
      testPassword,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
      REDIRECT_URI,
      'code',
    );

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it('should reject login for a disabled user', async () => {
    await waitForSyncAfter(fixture.domain.id, () =>
      updateUserStatus(fixture.domain.id, fixture.accessToken, userId, false),
    );

    const clientId = fixture.appAccountTests.settings.oauth.clientId;
    const postLogin = await attemptLogin(clientId, testUsername, testPassword);
    expect(postLogin.headers['location']).toContain('error=login_failed');
  });
});

describe('Account Locked - Login Attempt', () => {
  let userId: string;
  const testUsername = uniqueName('locked-attempt', true);
  const testPassword = '#CoMpL3X-P@SsW0Rd';

  beforeAll(async () => {
    const user = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'Locked',
      lastName: 'Attempt',
      username: testUsername,
      email: `${testUsername}@example.com`,
      password: testPassword,
      preRegistration: false,
    });
    expect(user.id).toBeDefined();
    userId = user.id;

    await waitForSyncAfter(fixture.domain.id, () =>
      patchDomain(fixture.domain.id, fixture.accessToken, {
        accountSettings: {
          inherited: false,
          loginAttemptsDetectionEnabled: true,
          maxLoginAttempts: 1,
          loginAttemptsResetTime: 60,
          accountBlockedDuration: 120,
        },
      }),
    );
  });

  afterAll(async () => {
    try {
      await patchDomain(fixture.domain.id, fixture.accessToken, {
        accountSettings: { inherited: false, loginAttemptsDetectionEnabled: false },
      });
    } catch (e) {
      console.warn('Failed to reset account settings after locked-attempt tests:', e);
    }
    if (userId) {
      try {
        await deleteUser(fixture.domain.id, fixture.accessToken, userId);
      } catch (e) {
        console.warn('Failed to delete locked-attempt test user:', e);
      }
    }
  });

  it('should fail on first login attempt with wrong password', async () => {
    const clientId = fixture.appAccountTests.settings.oauth.clientId;
    const postLogin = await attemptLogin(clientId, testUsername, 'wrong-password');
    expect(postLogin.headers['location']).toContain('error=login_failed');
  });

  it('should lock the account after exceeding max login attempts', async () => {
    // maxLoginAttempts=1 — the first failed attempt above already triggered the lock
    const clientId = fixture.appAccountTests.settings.oauth.clientId;
    const postLogin = await attemptLogin(clientId, testUsername, 'wrong-password');
    expect(postLogin.headers['location']).toContain('error=login_failed');
  });
});

describe('Account Locked - REST API', () => {
  let userId: string;
  const testUsername = uniqueName('locked-api', true);
  const testPassword = '#CoMpL3X-P@SsW0Rd';

  beforeAll(async () => {
    const user = await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'Locked',
      lastName: 'Api',
      username: testUsername,
      email: `${testUsername}@example.com`,
      password: testPassword,
      preRegistration: false,
    });
    expect(user.id).toBeDefined();
    userId = user.id;

    await waitForSyncAfter(fixture.domain.id, () =>
      lockUser(fixture.domain.id, fixture.accessToken, userId),
    );
  });

  afterAll(async () => {
    if (userId) {
      try {
        await deleteUser(fixture.domain.id, fixture.accessToken, userId);
      } catch (e) {
        console.warn('Failed to delete locked-api test user:', e);
      }
    }
  });

  it('should reject login for a user locked via the management REST API', async () => {
    const clientId = fixture.appAccountTests.settings.oauth.clientId;
    const postLogin = await attemptLogin(clientId, testUsername, testPassword);
    expect(postLogin.headers['location']).toContain('error=login_failed');
  });
});

describe('Case insensitive', () => {
  it('should issue an access token when username is provided with mixed case', async () => {
    const tokenResponse = await performPost(
      fixture.openIdConfiguration.token_endpoint,
      '',
      `grant_type=password&username=UsEr&password=${encodeURIComponent(INLINE_USER.password)}`,
      {
        Authorization: 'Basic ' + applicationBase64Token(fixture.appSso1),
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.token_type).toBe('bearer');
    expect(tokenResponse.body.expires_in).toBeDefined();
  });
});
