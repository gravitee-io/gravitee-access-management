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
import { readFileSync } from 'fs';
import { join } from 'path';

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
} from '@management-commands/domain-management-commands';
import { getDomainApi } from '@management-commands/service/utils';
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import { NewFormTemplateEnum } from '@management-models/NewForm';

import { getGatewayBaseUrl, oauthWebSettings, quietly, uniqueTestName } from '../utils/fixture-helpers';
import { AM2193_LOGIN_FORM_MARKER_TEXT } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/webauthn-helpers';

const LOGIN_HTML_PATH = join(
  __dirname,
  '../../../gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-core/src/main/resources/webroot/views/login.html',
);

export type LoginFormGatewayBundle = {
  domain: Domain;
  adminToken: string;
  gatewayUrl: string;
  app: Application;
};

export const test = base.extend<{ loginFormGatewayBundle: LoginFormGatewayBundle }>({
  loginFormGatewayBundle: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();
    const name = uniqueTestName('pw-login-form');
    const domain = await quietly(() => createDomain(adminToken, name, 'Playwright domain login form (AM-2193)'));

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
      }),
    );

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-lf-app'), domain, adminToken, 'WEB', {
        identityProviders: new Set([{ identity: `default-idp-${domain.id}`, priority: 0 }]),
        settings: {
          oauth: oauthWebSettings(REDIRECT_URI),
          advanced: { skipConsent: true },
        },
      }),
    );

    let loginHtml = readFileSync(LOGIN_HTML_PATH, 'utf-8');
    const marker = `<p id="pw-am-2193-marker">${AM2193_LOGIN_FORM_MARKER_TEXT}</p>`;
    if (!loginHtml.includes('<body>')) {
      throw new Error(`Expected <body> in login template at ${LOGIN_HTML_PATH}`);
    }
    loginHtml = loginHtml.replace('<body>', `<body>${marker}`);

    // Default LOGIN template from GET has no persisted id; create a domain form before sync.
    await quietly(() =>
      getDomainApi(adminToken).createForm({
        organizationId: process.env.AM_DEF_ORG_ID!,
        environmentId: process.env.AM_DEF_ENV_ID!,
        domain: domain.id,
        newForm: {
          template: NewFormTemplateEnum.Login,
          content: loginHtml,
          enabled: true,
        },
      }),
    );

    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));
    await waitForOidcReady(domain.hrid, { timeoutMs: 30000, intervalMs: 500 });

    const gatewayUrl = `${getGatewayBaseUrl()}/${domain.hrid}`;

    const bundle: LoginFormGatewayBundle = {
      domain,
      adminToken,
      gatewayUrl,
      app,
    };

    await use(bundle);

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },
});

export { expect } from '@playwright/test';
