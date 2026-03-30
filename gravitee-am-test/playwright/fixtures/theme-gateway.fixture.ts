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
} from '@management-commands/domain-management-commands';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';
import { createTheme, deleteTheme, updateTheme } from '@management-commands/theme-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { Theme } from '@management-models/Theme';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import {
  API_USER_PASSWORD,
  GATEWAY_THEME_LOGO_URL,
  GATEWAY_THEME_PRIMARY_BUTTON_HEX,
  GATEWAY_THEME_PRIMARY_TEXT_HEX,
} from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

const OAUTH_WEB = {
  redirectUris: [REDIRECT_URI],
  grantTypes: ['authorization_code'],
  scopeSettings: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
  ],
};

export type ThemeGatewayBundle = {
  domain: Domain;
  adminToken: string;
  gatewayUrl: string;
  app: Application;
  theme: Theme;
  credentials: { username: string; password: string };
};

export const test = base.extend<{ themeGatewayBundle: ThemeGatewayBundle }>({
  themeGatewayBundle: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();
    const name = uniqueTestName('pw-theme-gateway');
    const domain = await quietly(() => createDomain(adminToken, name, 'Playwright domain theme (AM-2172)'));

    const credentials = { username: 'pw_theme_user', password: API_USER_PASSWORD };

    await quietly(() => deleteIdp(domain.id, adminToken, `default-idp-${domain.id}`));

    const idp = await quietly(() =>
      createIdp(domain.id, adminToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        name: uniqueTestName('pw-theme-idp'),
        configuration: JSON.stringify({
          users: [
            {
              firstname: 'Theme',
              lastname: 'User',
              username: credentials.username,
              password: credentials.password,
            },
          ],
        }),
      }),
    );

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-theme-app'), domain, adminToken, 'WEB', {
        identityProviders: new Set([{ identity: idp.id, priority: 0 }]),
        settings: {
          oauth: OAUTH_WEB,
          advanced: { skipConsent: true },
        },
      }),
    );

    const theme = await quietly(() =>
      createTheme(domain.id, adminToken, {
        name: uniqueTestName('pw-theme'),
        locale: 'en',
      }),
    );

    const themeId = theme.id;
    if (themeId === undefined || themeId === '') {
      throw new Error('createTheme returned no theme id');
    }

    await quietly(() =>
      updateTheme(domain.id, adminToken, themeId, {
        logoUrl: GATEWAY_THEME_LOGO_URL,
        primaryButtonColorHex: GATEWAY_THEME_PRIMARY_BUTTON_HEX,
        primaryTextColorHex: GATEWAY_THEME_PRIMARY_TEXT_HEX,
      }),
    );

    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));
    await waitForOidcReady(domain.hrid, { timeoutMs: 30000, intervalMs: 500 });

    const baseUrl = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
    const gatewayUrl = `${baseUrl}/${domain.hrid}`;

    const bundle: ThemeGatewayBundle = {
      domain,
      adminToken,
      gatewayUrl,
      app,
      theme,
      credentials,
    };

    await use(bundle);

    await quietly(() => deleteTheme(domain.id, adminToken, themeId));
    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },
});

export { expect } from '@playwright/test';
