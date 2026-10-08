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
  safeDeleteDomain,
  startDomain,
  waitForDomainSync,
  waitForOAuthAuthorizeRedirectsToLogin,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { User } from '@management-models/User';

import { getGatewayBaseUrl, oauthWebSettings, quietly, uniqueTestName } from '../utils/fixture-helpers';
import { REDIRECT_URI } from '../utils/webauthn-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';

export type MagicLinkGatewayBundle = {
  domain: Domain;
  adminToken: string;
  gatewayUrl: string;
  app: Application;
  user: User;
  /** Turns the domain-level magic link toggle on or off and waits for the domain to sync. */
  setMagicLinkEnabled: (enabled: boolean) => Promise<void>;
};

export const test = base.extend<{ magicLinkBundle: MagicLinkGatewayBundle }>({
  magicLinkBundle: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();
    const domain = await quietly(() => createDomain(adminToken, uniqueTestName('pw-mlk'), 'Playwright magic link'));

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
          magicLinkAuthEnabled: true,
        },
      }),
    );

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-mlk-app'), domain, adminToken, 'WEB', {
        identityProviders: new Set([{ identity: `default-idp-${domain.id}`, priority: 0 }]),
        settings: {
          oauth: oauthWebSettings(REDIRECT_URI),
          advanced: { skipConsent: true },
        },
      }),
    );

    const user = await quietly(() =>
      createUser(domain.id, adminToken, {
        username: uniqueTestName('mlk_user'),
        email: `${uniqueTestName('mlk')}@mail.com`,
        firstName: 'Magic',
        lastName: 'Link',
        password: API_USER_PASSWORD,
      }),
    );

    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));
    await waitForOidcReady(domain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    await waitForOAuthAuthorizeRedirectsToLogin(domain.hrid, app.settings.oauth.clientId, REDIRECT_URI);

    const bundle: MagicLinkGatewayBundle = {
      domain,
      adminToken,
      gatewayUrl: `${getGatewayBaseUrl()}/${domain.hrid}`,
      app,
      user,
      setMagicLinkEnabled: async (enabled: boolean) => {
        await quietly(() => patchDomain(domain.id, adminToken, { loginSettings: { magicLinkAuthEnabled: enabled } }));
        await quietly(() => waitForDomainSync(domain.id));
      },
    };

    await use(bundle);

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },
});

export { expect } from '@playwright/test';
