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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createIdp } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';

export const PASSWORD = '#CoMpL3X-P@SsW0Rd';
export const WRONG_PASSWORD = 'wrong-password';
export const INLINE_USERNAME = 'Lock.User';

const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';

const ACCOUNT_SETTINGS = {
  inherited: false,
  loginAttemptsDetectionEnabled: true,
  maxLoginAttempts: 3,
  loginAttemptsResetTime: 300,
  accountBlockedDuration: 300,
};

const OAUTH = {
  redirectUris: [REDIRECT_URI],
  grantTypes: ['authorization_code'],
  scopeSettings: [{ scope: 'openid' }],
};

export interface BruteForceFixture {
  accessToken: string;
  domain: any;
  inlineApp: any;
  defaultIdpApp: any;
  /** Posts the login form and follows the redirect. True when the flow ends on the code redirect. */
  loginSucceeds: (app: any, username: string, password: string) => Promise<boolean>;
  /** Retries a login until it succeeds, for changes that the gateway gets through sync. */
  eventuallyLoginSucceeds: (app: any, username: string, password: string) => Promise<boolean>;
  failLogins: (app: any, usernames: string[]) => Promise<void>;
  createDefaultIdpUser: (username: string) => Promise<any>;
  cleanup: () => Promise<void>;
}

export const setupBruteForceFixture = async (): Promise<BruteForceFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain, oidcConfig } = await setupDomainForTest(uniqueName('brute-force', true), { accessToken, waitForStart: true });

  const inlineIdp = await createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({
      users: [{ firstname: 'Lock', lastname: 'User', username: INLINE_USERNAME, password: PASSWORD, email: `${uniqueName('lock', true)}@example.com` }],
    }),
    name: uniqueName('inline', true),
  });
  const inlineApp = await createTestApp(uniqueName('inline-app', true), domain, accessToken, 'WEB', {
    identityProviders: new Set([{ identity: inlineIdp.id, priority: -1 }]),
    settings: { oauth: OAUTH, advanced: { skipConsent: true } },
  });
  const defaultIdpApp = await createTestApp(uniqueName('default-idp-app', true), domain, accessToken, 'WEB', {
    identityProviders: new Set([{ identity: `default-idp-${domain.id}`, priority: -1 }]),
    settings: { oauth: OAUTH, advanced: { skipConsent: true } },
  });
  await patchApplication(domain.id, accessToken, { settings: { account: ACCOUNT_SETTINGS } }, inlineApp.id);
  await patchApplication(domain.id, accessToken, { settings: { account: ACCOUNT_SETTINGS } }, defaultIdpApp.id);
  await waitForDomainSync(domain.id, accessToken);

  const loginSucceeds = async (app: any, username: string, password: string) => {
    const clientId = app.settings.oauth.clientId;
    const authResponse = await initiateLoginFlow(clientId, oidcConfig, domain, 'code', REDIRECT_URI);
    const postLogin = await login(authResponse, username, clientId, password);
    const location = postLogin.headers['location'] ?? '';
    if (location.includes('error=')) {
      return false;
    }
    const authorize = await performGet(location, '', { Cookie: postLogin.headers['set-cookie'] });
    return (authorize.headers['location'] ?? '').startsWith(`${REDIRECT_URI}?code=`);
  };

  const eventuallyLoginSucceeds = (app: any, username: string, password: string) =>
    retryUntil(
      () => loginSucceeds(app, username, password),
      (ok) => ok,
      { timeoutMillis: 20000, intervalMillis: 1000 },
    ).catch(() => false);

  const failLogins = async (app: any, usernames: string[]) => {
    for (const username of usernames) {
      expect(await loginSucceeds(app, username, WRONG_PASSWORD)).toBe(false);
    }
  };

  const createDefaultIdpUser = async (username: string) => {
    const user = await createUser(domain.id, accessToken, {
      firstName: 'Mixed',
      lastName: 'Case',
      username,
      email: `${uniqueName('mail', true)}@example.com`,
      password: PASSWORD,
      preRegistration: false,
    });
    expect(await eventuallyLoginSucceeds(defaultIdpApp, username, PASSWORD)).toBe(true);
    return user;
  };

  return {
    accessToken,
    domain,
    inlineApp,
    defaultIdpApp,
    loginSucceeds,
    eventuallyLoginSucceeds,
    failLogins,
    createDefaultIdpUser,
    cleanup: () => safeDeleteDomain(domain.id, accessToken),
  };
};
