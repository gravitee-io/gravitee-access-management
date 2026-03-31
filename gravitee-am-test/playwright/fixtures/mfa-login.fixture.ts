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
  waitForOidcReady,
  waitForOAuthAuthorizeRedirectsToLogin,
} from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser, deleteUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { User } from '@management-models/User';

import { getGatewayBaseUrl, quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD, MOCK_MFA_CODE } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

/* ------------------------------------------------------------------ */
/*  Fixture types                                                      */
/* ------------------------------------------------------------------ */

export type MfaLoginFixtures = {
  adminToken: string;
  mfaDomain: Domain;
  /** IDs of mock factors created for this domain */
  factorIds: string[];
  mfaApp: Application;
  mfaUser: User;
  /** Gateway URL for this domain (e.g. http://localhost:8092/domain-hrid) */
  gatewayUrl: string;
  /** Number of mock factors to create. Override via test.use(). Default: 1 */
  factorCount: number;
  /** Whether MFA enrollment is forced (no skip). Override via test.use(). Default: true */
  forceEnrollment: boolean;
};

/* ------------------------------------------------------------------ */
/*  Fixture                                                            */
/* ------------------------------------------------------------------ */

export const test = base.extend<MfaLoginFixtures>({
  // Configurable options — override per-describe via test.use()
  factorCount: [1, { option: true }],
  forceEnrollment: [true, { option: true }],

  adminToken: async ({}, use) => {
    await use(await requestAdminAccessToken());
  },

  mfaDomain: async ({ adminToken }, use) => {
    const name = uniqueTestName('pw-mfa-login');
    const domain = await quietly(() => createDomain(adminToken, name, 'MFA login Playwright test'));
    // Domain is NOT started here — gatewayUrl starts it after all resources are created.
    await use(domain);
    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },

  factorIds: async ({ adminToken, mfaDomain, factorCount }, use) => {
    const ids: string[] = [];
    for (let i = 0; i < factorCount; i++) {
      const factor = await quietly(() =>
        createFactor(mfaDomain.id, adminToken, {
          type: 'mock-am-factor',
          factorType: 'MOCK',
          configuration: `{"code":"${MOCK_MFA_CODE}"}`,
          name: uniqueTestName(`mock-factor-${i}`),
        }),
      );
      ids.push(factor.id);
    }
    await use(ids);
  },

  mfaApp: async ({ adminToken, mfaDomain, factorIds, forceEnrollment }, use) => {
    const idpSet = await getAllIdps(mfaDomain.id, adminToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) throw new Error('No IdP found');

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-mfa-login-app'), mfaDomain, adminToken, 'WEB', {
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
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
      }),
    );

    // Enable MFA with mock factor(s)
    await quietly(() =>
      patchApplication(
        mfaDomain.id,
        adminToken,
        {
          settings: {
            mfa: {
              factor: {
                defaultFactorId: factorIds[0],
                applicationFactors: factorIds.map((id) => ({ id, selectionRule: '' })),
              },
              enroll: {
                active: true,
                forceEnrollment,
                type: 'REQUIRED',
              },
              challenge: {
                active: true,
                type: 'REQUIRED',
              },
            },
          },
        },
        app.id,
      ),
    );

    await use(app);
  },

  mfaUser: async ({ adminToken, mfaDomain }, use) => {
    const user = await quietly(() =>
      createUser(mfaDomain.id, adminToken, {
        firstName: 'MFA',
        lastName: 'Login',
        email: `${uniqueTestName('mfa-login-user')}@example.com`,
        username: uniqueTestName('mfa-login-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );
    await use(user);
    await quietly(() => deleteUser(mfaDomain.id, adminToken, user.id).catch(() => {}));
  },

  gatewayUrl: async ({ adminToken, mfaDomain, mfaApp, mfaUser, factorIds }, use) => {
    // Force dependency resolution — all resources created before domain start
    void mfaApp;
    void mfaUser;
    void factorIds;
    await quietly(() => startDomain(mfaDomain.id, adminToken));
    await quietly(() => waitForDomainSync(mfaDomain.id));
    await waitForOidcReady(mfaDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    await waitForOAuthAuthorizeRedirectsToLogin(mfaDomain.hrid, mfaApp.settings.oauth.clientId, REDIRECT_URI);
    await use(`${getGatewayBaseUrl()}/${mfaDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
export { MOCK_MFA_CODE } from '../utils/test-constants';
export {
  buildAuthorizeUrl,
  submitLogin,
  enrollMockFactor,
  completeMfaChallenge,
  handleConsentIfPresent,
  fullMfaLogin,
} from '../utils/mfa-helpers';
