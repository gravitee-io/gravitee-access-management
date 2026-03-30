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
import { patchApplication } from '@management-commands/application-management-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { User } from '@management-models/User';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD, MOCK_MFA_CODE } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

export type RecoveryFlowFixtures = {
  adminToken: string;
  recoveryDomain: Domain;
  recoveryApp: Application;
  recoveryUser: User;
  mockFactorId: string;
  recoveryFactorId: string;
  gatewayUrl: string;
};

export const test = base.extend<RecoveryFlowFixtures>({
  adminToken: async ({}, use) => {
    await use(await requestAdminAccessToken());
  },

  recoveryDomain: async ({ adminToken }, use) => {
    const name = uniqueTestName('pw-recovery-mfa');
    const domain = await quietly(() =>
      createDomain(adminToken, name, 'Phase 7 AM-2216 — MFA recovery codes'),
    );
    await use(domain);
    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },

  mockFactorId: async ({ adminToken, recoveryDomain }, use) => {
    const factor = await quietly(() =>
      createFactor(recoveryDomain.id, adminToken, {
        type: 'mock-am-factor',
        factorType: 'MOCK',
        configuration: `{"code":"${MOCK_MFA_CODE}"}`,
        name: uniqueTestName('mock-factor'),
      }),
    );
    await use(factor.id);
  },

  recoveryFactorId: async ({ adminToken, recoveryDomain }, use) => {
    const factor = await quietly(() =>
      createFactor(recoveryDomain.id, adminToken, {
        type: 'recovery-code-am-factor',
        factorType: 'Recovery Code',
        configuration: '{"digit":5,"count":6}',
        name: uniqueTestName('recovery-factor'),
      }),
    );
    await use(factor.id);
  },

  recoveryApp: async ({ adminToken, recoveryDomain, mockFactorId, recoveryFactorId }, use) => {
    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-recovery-app'), recoveryDomain, adminToken, 'WEB', {
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
        identityProviders: new Set([{ identity: `default-idp-${recoveryDomain.id}`, priority: 0 }]),
      }),
    );

    await quietly(() =>
      patchApplication(recoveryDomain.id, adminToken, {
        settings: {
          mfa: {
            factor: {
              defaultFactorId: mockFactorId,
              applicationFactors: [
                { id: mockFactorId, selectionRule: '' },
                { id: recoveryFactorId, selectionRule: '' },
              ],
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
          },
          advanced: { skipConsent: true },
        },
      }, app.id),
    );

    await use(app);
  },

  recoveryUser: async ({ adminToken, recoveryDomain }, use) => {
    const user = await quietly(() =>
      createUser(recoveryDomain.id, adminToken, {
        firstName: 'Recovery',
        lastName: 'Codes',
        email: `${uniqueTestName('recovery-user')}@example.com`,
        username: uniqueTestName('recovery-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );
    await use(user);
    await quietly(async () => {
      try {
        await deleteUser(recoveryDomain.id, adminToken, user.id);
      } catch {
        // domain teardown may cascade
      }
    });
  },

  gatewayUrl: async ({ adminToken, recoveryDomain, recoveryApp, recoveryUser }, use) => {
    if (!recoveryApp?.id || !recoveryUser?.id) {
      throw new Error('gatewayUrl: application or user not initialised');
    }
    await quietly(() => startDomain(recoveryDomain.id, adminToken));
    await quietly(() => waitForDomainSync(recoveryDomain.id, { timeoutMillis: 90_000, intervalMillis: 500 }));
    await waitForOidcReady(recoveryDomain.hrid, { timeoutMs: 45_000, intervalMs: 500 });
    await waitForOAuthAuthorizeRedirectsToLogin(
      recoveryDomain.hrid,
      recoveryApp.settings.oauth.clientId,
      REDIRECT_URI,
      { timeoutMs: 90_000, intervalMs: 500 },
    );
    const gatewayBase = (process.env.AM_GATEWAY_URL || 'http://localhost:8092').replace(/\/$/, '');
    await use(`${gatewayBase}/${recoveryDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
export {
  buildAuthorizeUrl,
  submitLogin,
  enrollMockFactor,
  completeMfaChallenge,
  handleConsentIfPresent,
  readFirstRecoveryCodeFromPage,
  submitRecoveryCodesContinue,
  openMfaChallengeAlternatives,
  selectRecoveryFactorOnAlternativesPage,
} from '../utils/mfa-helpers';
