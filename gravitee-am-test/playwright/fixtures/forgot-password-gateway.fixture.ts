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
import { test as base } from '@playwright/test';

import crossFetch from 'cross-fetch';
globalThis.fetch = crossFetch;

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  patchDomain,
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOidcReady,
  waitForOAuthAuthorizeRedirectsToLogin,
} from '@management-commands/domain-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { User } from '@management-models/User';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { REDIRECT_URI } from '../utils/webauthn-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';

const OAUTH_WEB = {
  redirectUris: [REDIRECT_URI],
  grantTypes: ['authorization_code'],
  scopeSettings: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
  ],
};

export type ForgotPasswordGatewayBundle = {
  domain: Domain;
  adminToken: string;
  gatewayUrl: string;
  app: Application;
  user: User;
};

export const test = base.extend<{ forgotPasswordBundle: ForgotPasswordGatewayBundle }>({
  forgotPasswordBundle: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();
    const name = uniqueTestName('pw-fpw');
    const domain = await quietly(() => createDomain(adminToken, name, 'Playwright forgot password (Phase 8)'));

    const emailLocal = `${uniqueTestName('fpw')}@mail.com`;
    const username = uniqueTestName('fpw_user');
    const initialPassword = API_USER_PASSWORD;

    await quietly(() =>
      patchDomain(domain.id, adminToken, {
        master: true,
        oidc: {
          clientRegistrationSettings: {
            allowLocalhostRedirectUri: true,
            allowHttpSchemeRedirectUri: true,
            allowWildCardRedirectUri: true,
          },
        },
        loginSettings: {
          forgotPasswordEnabled: true,
        },
      }),
    );

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-fpw-app'), domain, adminToken, 'WEB', {
        identityProviders: new Set([{ identity: `default-idp-${domain.id}`, priority: 0 }]),
        settings: {
          oauth: OAUTH_WEB,
          account: {
            inherited: false,
            autoLoginAfterResetPassword: true,
            redirectUriAfterResetPassword: REDIRECT_URI,
          },
          passwordSettings: {
            inherited: false,
            minLength: 5,
            maxLength: 24,
          },
          advanced: { skipConsent: true },
        },
      }),
    );

    const user = await quietly(() =>
      createUser(domain.id, adminToken, {
        username,
        email: emailLocal,
        firstName: 'Forgot',
        lastName: 'Pw',
        password: initialPassword,
      }),
    );

    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));
    await waitForOidcReady(domain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    await waitForOAuthAuthorizeRedirectsToLogin(domain.hrid, app.settings.oauth.clientId, REDIRECT_URI);

    const baseUrl = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
    const gatewayUrl = `${baseUrl}/${domain.hrid}`;

    const bundle: ForgotPasswordGatewayBundle = {
      domain,
      adminToken,
      gatewayUrl,
      app,
      user,
    };

    await use(bundle);

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },
});

export { expect } from '@playwright/test';
