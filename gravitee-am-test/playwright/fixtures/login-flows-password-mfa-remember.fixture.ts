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
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOAuthAuthorizeRedirectsToLogin,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createUser, deleteUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { createDevice } from '@management-commands/device-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { User } from '@management-models/User';

import { getGatewayBaseUrl, quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD, MOCK_MFA_CODE } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

export { MOCK_MFA_CODE } from '../utils/test-constants';

export type PasswordMfaRememberFixtures = {
  adminToken: string;
  rememberDomain: Domain;
  rememberApp: Application;
  rememberUser: User;
  rememberFactorId: string;
  rememberDeviceId: string;
  gatewayUrl: string;
};

export const test = base.extend<PasswordMfaRememberFixtures>({
  adminToken: async ({}, use) => {
    await use(await requestAdminAccessToken());
  },

  rememberDomain: async ({ adminToken }, use) => {
    const name = uniqueTestName('pw-mfa-remember-pw');
    const domain = await quietly(() =>
      createDomain(adminToken, name, 'Phase 7 AM-2218 — password MFA + remember device'),
    );
    await use(domain);
    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },

  rememberFactorId: async ({ adminToken, rememberDomain }, use) => {
    const factor = await quietly(() =>
      createFactor(rememberDomain.id, adminToken, {
        type: 'mock-am-factor',
        factorType: 'MOCK',
        configuration: `{"code":"${MOCK_MFA_CODE}"}`,
        name: uniqueTestName('mock-factor'),
      }),
    );
    await use(factor.id);
  },

  rememberDeviceId: async ({ adminToken, rememberDomain }, use) => {
    const device = await quietly(() =>
      createDevice(rememberDomain.id, adminToken, {
        type: 'cookie-device-identifier',
        configuration: '{}',
        name: uniqueTestName('cookie-device'),
      }),
    );
    await use(device.id);
  },

  rememberApp: async ({ adminToken, rememberDomain, rememberFactorId, rememberDeviceId }, use) => {
    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-mfa-remember-app'), rememberDomain, adminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              { scope: 'profile', defaultScope: true },
            ],
          },
        },
        identityProviders: new Set([{ identity: `default-idp-${rememberDomain.id}`, priority: 0 }]),
      }),
    );

    await quietly(() =>
      patchApplication(rememberDomain.id, adminToken, {
        settings: {
          mfa: {
            factor: {
              defaultFactorId: rememberFactorId,
              applicationFactors: [{ id: rememberFactorId, selectionRule: '' }],
            },
            enroll: {
              active: true,
              forceEnrollment: true,
              type: 'REQUIRED',
            },
            challenge: {
              active: true,
              type: 'REQUIRED',
            },
            rememberDevice: {
              active: true,
              deviceIdentifierId: rememberDeviceId,
              expirationTimeSeconds: 600,
            },
          },
          advanced: { skipConsent: true },
        },
      }, app.id),
    );

    await use(app);
  },

  rememberUser: async ({ adminToken, rememberDomain }, use) => {
    const user = await quietly(() =>
      createUser(rememberDomain.id, adminToken, {
        firstName: 'Remember',
        lastName: 'Device',
        email: `${uniqueTestName('mfa-remember-user')}@example.com`,
        username: uniqueTestName('mfa-remember-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );
    await use(user);
    await quietly(() => deleteUser(rememberDomain.id, adminToken, user.id).catch(() => {}));
  },

  gatewayUrl: async ({ adminToken, rememberDomain, rememberApp, rememberUser }, use) => {
    if (!rememberApp?.id || !rememberUser?.id) {
      throw new Error('gatewayUrl: application or user not initialised');
    }
    await quietly(() => startDomain(rememberDomain.id, adminToken));
    await quietly(() => waitForDomainSync(rememberDomain.id, { timeoutMillis: 90_000, intervalMillis: 500 }));
    await waitForOidcReady(rememberDomain.hrid, { timeoutMs: 45_000, intervalMs: 500 });
    await waitForOAuthAuthorizeRedirectsToLogin(
      rememberDomain.hrid,
      rememberApp.settings.oauth.clientId,
      REDIRECT_URI,
      { timeoutMs: 90_000, intervalMs: 500 },
    );
    await use(`${getGatewayBaseUrl()}/${rememberDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
export {
  buildAuthorizeUrl,
  submitLogin,
  enrollMockFactor,
  completeMfaChallenge,
  handleConsentIfPresent,
} from '../utils/mfa-helpers';
