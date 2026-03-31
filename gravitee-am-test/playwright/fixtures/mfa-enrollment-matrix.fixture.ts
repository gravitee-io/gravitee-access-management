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
import type { PatchEnrollSettingsTypeEnum } from '@management-models/PatchEnrollSettings';
import type { PatchChallengeSettingsTypeEnum } from '@management-models/PatchChallengeSettings';

import { getGatewayBaseUrl, quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD, MOCK_MFA_CODE } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

/* ------------------------------------------------------------------ */
/*  Fixture types                                                      */
/* ------------------------------------------------------------------ */

export type MfaMatrixFixtures = {
  adminToken: string;
  matrixDomain: Domain;
  factorId: string;
  matrixApp: Application;
  matrixUser: User;
  gatewayUrl: string;

  /* Enrollment settings — override per-describe via test.use() */
  enrollActive: boolean;
  enrollType: PatchEnrollSettingsTypeEnum;
  enrollForce: boolean;
  enrollRule: string;
  enrollSkipActive: boolean;
  enrollSkipRule: string;
  /** When set, sent as enroll.skipTimeSeconds (enrollment skip window) */
  enrollSkipTimeSeconds: number | undefined;

  /* Challenge settings — override per-describe via test.use() */
  challengeActive: boolean;
  challengeType: PatchChallengeSettingsTypeEnum;
  challengeRule: string;
};

/* ------------------------------------------------------------------ */
/*  Fixture                                                            */
/* ------------------------------------------------------------------ */

export const test = base.extend<MfaMatrixFixtures>({
  /* Configurable options with defaults */
  enrollActive: [true, { option: true }],
  enrollType: ['REQUIRED', { option: true }],
  enrollForce: [true, { option: true }],
  enrollRule: ['', { option: true }],
  enrollSkipActive: [false, { option: true }],
  enrollSkipRule: ['', { option: true }],
  enrollSkipTimeSeconds: [undefined, { option: true }],
  challengeActive: [false, { option: true }],
  challengeType: ['REQUIRED', { option: true }],
  challengeRule: ['', { option: true }],

  adminToken: async ({}, use) => {
    await use(await requestAdminAccessToken());
  },

  matrixDomain: async ({ adminToken }, use) => {
    const name = uniqueTestName('pw-mfa-matrix');
    const domain = await quietly(() => createDomain(adminToken, name, 'MFA enrollment matrix Playwright test'));
    await use(domain);
    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },

  factorId: async ({ adminToken, matrixDomain }, use) => {
    const factor = await quietly(() =>
      createFactor(matrixDomain.id, adminToken, {
        type: 'mock-am-factor',
        factorType: 'MOCK',
        configuration: `{"code":"${MOCK_MFA_CODE}"}`,
        name: uniqueTestName('mock-factor'),
      }),
    );
    await use(factor.id);
  },

  matrixApp: async (
    {
      adminToken,
      matrixDomain,
      factorId,
      enrollActive,
      enrollType,
      enrollForce,
      enrollRule,
      enrollSkipActive,
      enrollSkipRule,
      enrollSkipTimeSeconds,
      challengeActive,
      challengeType,
      challengeRule,
    },
    use,
  ) => {
    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-matrix-app'), matrixDomain, adminToken, 'WEB', {
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
        identityProviders: new Set([{ identity: `default-idp-${matrixDomain.id}`, priority: 0 }]),
      }),
    );

    await quietly(() =>
      patchApplication(
        matrixDomain.id,
        adminToken,
        {
          settings: {
            mfa: {
              factor: {
                defaultFactorId: factorId,
                applicationFactors: [{ id: factorId, selectionRule: '' }],
              },
              enroll: {
                active: enrollActive,
                type: enrollType,
                forceEnrollment: enrollForce,
                enrollmentRule: enrollRule,
                enrollmentSkipActive: enrollSkipActive,
                enrollmentSkipRule: enrollSkipRule,
                ...(enrollSkipTimeSeconds == null ? {} : { skipTimeSeconds: enrollSkipTimeSeconds }),
              },
              challenge: {
                active: challengeActive,
                type: challengeType,
                challengeRule: challengeRule,
              },
            },
          },
        },
        app.id,
      ),
    );

    await use(app);
  },

  matrixUser: async ({ adminToken, matrixDomain }, use) => {
    const user = await quietly(() =>
      createUser(matrixDomain.id, adminToken, {
        firstName: 'Matrix',
        lastName: 'User',
        email: `${uniqueTestName('matrix-user')}@example.com`,
        username: uniqueTestName('matrix-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );
    await use(user);
    await quietly(() => deleteUser(matrixDomain.id, adminToken, user.id).catch(() => {}));
  },

  gatewayUrl: async ({ adminToken, matrixDomain, matrixApp, matrixUser, factorId }, use) => {
    if (!matrixApp.id || !matrixUser.id || !factorId) {
      throw new Error('MFA matrix gatewayUrl: application, user, or factor not ready');
    }
    await quietly(() => startDomain(matrixDomain.id, adminToken));
    await quietly(() => waitForDomainSync(matrixDomain.id, { timeoutMillis: 90_000, intervalMillis: 500 }));
    await waitForOidcReady(matrixDomain.hrid, { timeoutMs: 45_000, intervalMs: 500 });
    await waitForOAuthAuthorizeRedirectsToLogin(
      matrixDomain.hrid,
      matrixApp.settings.oauth.clientId,
      REDIRECT_URI,
      { timeoutMs: 90_000, intervalMs: 500 },
    );
    await use(`${getGatewayBaseUrl()}/${matrixDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
export {
  buildAuthorizeUrl,
  submitLogin,
  enrollMockFactor,
  completeMfaChallenge,
  handleConsentIfPresent,
  skipMfaEnrollment,
  secondAuthorizeExpectCallbackWithoutMfa,
  waitAfterAuthorizeThenLoginIfNeeded,
} from '../utils/mfa-helpers';
export { MOCK_MFA_CODE } from '../utils/test-constants';
