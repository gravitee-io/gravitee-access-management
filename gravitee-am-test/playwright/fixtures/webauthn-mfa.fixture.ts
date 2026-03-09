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

import { requestAdminAccessToken } from '../../api/commands/management/token-management-commands';
import {
  createDomain,
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOidcReady,
  patchDomain,
} from '../../api/commands/management/domain-management-commands';
import { getAllIdps } from '../../api/commands/management/idp-management-commands';
import { createUser, deleteUser } from '../../api/commands/management/user-management-commands';
import { createTestApp } from '../../api/commands/utils/application-commands';
import { createFactor } from '../../api/commands/management/factor-management-commands';
import { createDevice } from '../../api/commands/management/device-management-commands';
import { patchApplication } from '../../api/commands/management/application-management-commands';
import { Domain, Application, User } from '../../api/management/models';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/webauthn-helpers';

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

export const MOCK_MFA_CODE = '1234';

/* ------------------------------------------------------------------ */
/*  Fixture types                                                      */
/* ------------------------------------------------------------------ */

export type MfaWebAuthnFixtures = {
  mfaAdminToken: string;
  mfaDomain: Domain;
  mfaApp: Application;
  mfaUser: User;
  mfaFactorId: string;
  mfaDeviceId: string;
  gatewayUrl: string;
};

/* ------------------------------------------------------------------ */
/*  Fixture                                                            */
/* ------------------------------------------------------------------ */

export const test = base.extend<MfaWebAuthnFixtures>({
  mfaAdminToken: async ({}, use) => {
    await use(await requestAdminAccessToken());
  },

  mfaDomain: async ({ mfaAdminToken }, use) => {
    const name = uniqueTestName('pw-mfa-wa');
    const domain = await quietly(() => createDomain(mfaAdminToken, name, 'MFA + WebAuthn + Remember Device test'));

    // Domain is NOT started here — gatewayUrl starts it after app+user+factor
    // are created so the initial sync picks up all resources in one pass.
    await quietly(() =>
      patchDomain(domain.id, mfaAdminToken, {
        loginSettings: {
          inherited: false,
          passwordlessEnabled: true,
          passwordlessDeviceNamingEnabled: false,
        },
        webAuthnSettings: {
          origin: process.env.AM_GATEWAY_URL || 'http://localhost:8092',
          relyingPartyName: name,
          attestationConveyancePreference: 'NONE',
          authenticatorAttachment: 'PLATFORM',
          userVerification: 'REQUIRED',
          requireResidentKey: false,
          forceRegistration: true,
        },
      }),
    );

    await use(domain);
    await quietly(() => safeDeleteDomain(domain.id, mfaAdminToken));
  },

  mfaFactorId: async ({ mfaAdminToken, mfaDomain }, use) => {
    const factor = await quietly(() =>
      createFactor(mfaDomain.id, mfaAdminToken, {
        type: 'mock-am-factor',
        factorType: 'MOCK',
        configuration: `{"code":"${MOCK_MFA_CODE}"}`,
        name: uniqueTestName('mock-factor'),
      }),
    );
    await use(factor.id);
  },

  mfaDeviceId: async ({ mfaAdminToken, mfaDomain }, use) => {
    const device = await quietly(() =>
      createDevice(mfaDomain.id, mfaAdminToken, {
        type: 'cookie-device-identifier',
        configuration: '{}',
        name: uniqueTestName('cookie-device'),
      }),
    );
    await use(device.id);
  },

  mfaApp: async ({ mfaAdminToken, mfaDomain, mfaFactorId, mfaDeviceId }, use) => {
    const idpSet = await getAllIdps(mfaDomain.id, mfaAdminToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) throw new Error('No IdP found');

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-mfa-app'), mfaDomain, mfaAdminToken, 'WEB', {
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

    // Enable MFA with mock factor + remember device
    await quietly(() =>
      patchApplication(mfaDomain.id, mfaAdminToken, {
        settings: {
          mfa: {
            factor: {
              defaultFactorId: mfaFactorId,
              applicationFactors: [{ id: mfaFactorId, selectionRule: '' }],
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
              deviceIdentifierId: mfaDeviceId,
              expirationTimeSeconds: 180, // 3 minutes
            },
          },
        },
      }, app.id),
    );

    await use(app);
  },

  mfaUser: async ({ mfaAdminToken, mfaDomain }, use) => {
    const user = await quietly(() =>
      createUser(mfaDomain.id, mfaAdminToken, {
        firstName: 'MFA',
        lastName: 'WebAuthn',
        email: `${uniqueTestName('mfa-wa-user')}@example.com`,
        username: uniqueTestName('mfa-wa-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );
    await use(user);
    await quietly(async () => {
      try {
        await deleteUser(mfaDomain.id, mfaAdminToken, user.id);
      } catch {
        // domain teardown may cascade
      }
    });
  },

  gatewayUrl: async ({ mfaAdminToken, mfaDomain, mfaApp, mfaUser }, use) => {
    // All resources (domain config, app, user, factor, device) are created
    // before starting the domain, so the initial sync picks up everything.
    void mfaApp;
    void mfaUser;
    await quietly(() => startDomain(mfaDomain.id, mfaAdminToken));
    await quietly(() => waitForDomainSync(mfaDomain.id));
    await waitForOidcReady(mfaDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    const baseUrl = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
    await use(`${baseUrl}/${mfaDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
export {
  VirtualAuthenticator,
  addVirtualAuthenticator,
  simulateWebAuthnGesture,
  getCredentials,
  removeVirtualAuthenticator,
  handleConsentIfPresent,
  buildAuthorizeUrl,
  loginAndRegisterWebAuthn,
  passwordlessLogin,
  fullLoginWithMfaAndWebAuthn,
  navigateToWebAuthnLogin,
  clearSessionOnly,
  PASSWORDLESS_LINK_SELECTOR,
  REDIRECT_URI,
} from '../utils/webauthn-helpers';
