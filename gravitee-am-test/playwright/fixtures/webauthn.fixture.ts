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
import { Domain, Application, User } from '../../api/management/models';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/webauthn-helpers';

/* ------------------------------------------------------------------ */
/*  Re-export helpers so existing spec imports remain unchanged         */
/* ------------------------------------------------------------------ */

export {
  VirtualAuthenticator,
  addVirtualAuthenticator,
  simulateWebAuthnGesture,
  getCredentials,
  removeVirtualAuthenticator,
  handleConsentIfPresent,
  buildAuthorizeUrl,
  navigateToWebAuthnLogin,
  loginAndRegisterWebAuthn,
  passwordlessLogin,
  clearSessionOnly,
  PASSWORDLESS_LINK_SELECTOR,
  REDIRECT_URI,
} from '../utils/webauthn-helpers';

/* ------------------------------------------------------------------ */
/*  Fixture types                                                      */
/* ------------------------------------------------------------------ */

export type WebAuthnFixtures = {
  waAdminToken: string;
  waDomain: Domain;
  waApp: Application;
  waUser: User;
  /** Gateway URL for this domain (e.g. http://localhost:8092/domain-hrid) */
  gatewayUrl: string;
  /** Extra loginSettings merged into the domain config before start.
   *  Use test.use({ waExtraLoginSettings: { ... } }) to configure per-test. */
  waExtraLoginSettings: Record<string, unknown>;
};

/* ------------------------------------------------------------------ */
/*  Fixture                                                            */
/* ------------------------------------------------------------------ */

export const test = base.extend<WebAuthnFixtures>({
  waExtraLoginSettings: [{}, { option: true }],

  waAdminToken: async ({}, use) => {
    const token = await requestAdminAccessToken();
    await use(token);
  },

  waDomain: async ({ waAdminToken, waExtraLoginSettings }, use) => {
    const name = uniqueTestName('pw-webauthn');
    const domain = await quietly(() => createDomain(waAdminToken, name, 'WebAuthn Playwright test domain'));

    // Enable passwordless (WebAuthn) on the domain.
    // Domain is NOT started here — gatewayUrl starts it after app+user are
    // created so the initial sync picks up all resources in one pass.
    // waExtraLoginSettings is merged in so tests can configure features like
    // device recognition or enforce password before the domain starts,
    // avoiding a patchDomain on a running domain (which causes a redeploy gap).
    await quietly(() =>
      patchDomain(domain.id, waAdminToken, {
        loginSettings: {
          inherited: false,
          passwordlessEnabled: true,
          passwordlessDeviceNamingEnabled: false,
          ...waExtraLoginSettings,
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

    await quietly(() => safeDeleteDomain(domain.id, waAdminToken));
  },

  waApp: async ({ waAdminToken, waDomain }, use) => {
    const idpSet = await getAllIdps(waDomain.id, waAdminToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) throw new Error('No IdP found for WebAuthn domain');

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-wa-app'), waDomain, waAdminToken, 'WEB', {
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

    await use(app);
  },

  waUser: async ({ waAdminToken, waDomain }, use) => {
    const user = await quietly(() =>
      createUser(waDomain.id, waAdminToken, {
        firstName: 'WebAuthn',
        lastName: 'Test',
        email: `${uniqueTestName('wa-user')}@example.com`,
        username: uniqueTestName('wa-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );

    await use(user);

    await quietly(async () => {
      try {
        await deleteUser(waDomain.id, waAdminToken, user.id);
      } catch {
        // domain teardown may cascade
      }
    });
  },

  gatewayUrl: async ({ waAdminToken, waDomain, waApp, waUser }, use) => {
    // All resources (domain config, app, user) are created before starting
    // the domain, so the initial sync picks up everything in one pass.
    // This avoids the waitForNextSync race condition.
    void waApp;
    void waUser;
    await quietly(() => startDomain(waDomain.id, waAdminToken));
    await quietly(() => waitForDomainSync(waDomain.id));
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    const baseUrl = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
    await use(`${baseUrl}/${waDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
