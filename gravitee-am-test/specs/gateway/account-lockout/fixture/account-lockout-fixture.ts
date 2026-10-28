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
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForOidcReady,
  DomainOidcConfig,
} from '@management-commands/domain-management-commands';
import { getDefaultIdp } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getHeaderLocation, initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { User } from '@management-models/User';
import { Fixture } from '../../../test-fixture';

export const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';
export const USER_PASSWORD = '#CoMpL3X-P@SsW0Rd';

// Smaller than a production setup: the lock only has to be reached quickly and to end quickly.
export const MAX_LOGIN_ATTEMPTS = 3;
export const ACCOUNT_BLOCKED_DURATION_SECONDS = 20;

export interface AccountLockoutFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  user: User;
  /** Submits the login form and returns the redirect the gateway answers with. */
  submitLogin: (password: string) => Promise<any>;
  /**
   * Submits the login form and, when it is accepted, follows the authorization redirect.
   * A refused login is returned as is, so the caller can poll until it is accepted.
   */
  completeLogin: (password: string) => Promise<any>;
}

export const setupAccountLockoutFixture = async (): Promise<AccountLockoutFixture> => {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const domain = await createDomain(accessToken, uniqueName('account-lockout', true), 'Account lockout test domain');
  expect(domain.id).toBeDefined();
  const defaultIdp = await getDefaultIdp(domain.id, accessToken);

  // Everything is created before the domain starts, so the initial sync picks it all up.
  const application = await createTestApp(uniqueName('lockout-app', true), domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: [REDIRECT_URI],
        grantTypes: ['authorization_code'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
      advanced: { skipConsent: true },
      account: {
        inherited: false,
        loginAttemptsDetectionEnabled: true,
        maxLoginAttempts: MAX_LOGIN_ATTEMPTS,
        loginAttemptsResetTime: 60,
        accountBlockedDuration: ACCOUNT_BLOCKED_DURATION_SECONDS,
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });
  const clientId = application.settings.oauth.clientId;
  
  const username = uniqueName('erin', true).toLowerCase();
  const user = await createUser(domain.id, accessToken, {
    firstName: 'Erin',
    lastName: 'Lockout',
    email: `${username}@example.com`,
    username,
    password: USER_PASSWORD,
    preRegistration: false,
  });
  expect(user.id).toBeDefined();

  await startDomain(domain.id, accessToken);
  const started = await waitForDomainStart(domain);
  await waitForOidcReady(started.domain.hrid);
  const oidc = started.oidcConfig;

  const submitLogin = async (password: string) => {
    const authResponse = await initiateLoginFlow(clientId, oidc, started.domain, 'code', REDIRECT_URI);
    return login(authResponse, username, clientId, password);
  };

  return {
    accessToken,
    domain: started.domain,
    oidc,
    application,
    user,
    submitLogin,
    completeLogin: async (password: string) => {
      const postLogin = await submitLogin(password);
      return (postLogin.headers['location'] ?? '').includes('error=') ? postLogin : getHeaderLocation(postLogin);
    },
    cleanUp: async () => {
      await safeDeleteDomain(domain.id, accessToken);
    },
  };
};
