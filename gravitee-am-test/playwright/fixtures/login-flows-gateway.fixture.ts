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
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

const OAUTH_WEB = {
  redirectUris: [REDIRECT_URI],
  grantTypes: ['authorization_code'],
  scopeSettings: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
  ],
};

export type LoginFlowsGatewayBundle = {
  domain: Domain;
  adminToken: string;
  gatewayUrl: string;
  appIdentifierFirst: Application;
  appIdpDual: Application;
  userIdentifierFirst: { username: string; password: string };
  userIdpSel1: { username: string; password: string };
  userIdpSel2: { username: string; password: string };
};

export const test = base.extend<{ loginFlowsBundle: LoginFlowsGatewayBundle }>({
  loginFlowsBundle: async ({}, use) => {
    const adminToken = await requestAdminAccessToken();
    const name = uniqueTestName('pw-login-flows');
    const domain = await quietly(() => createDomain(adminToken, name, 'Playwright login flows (Phase 7)'));

    const userIdentifierFirst = { username: 'pwlf_if', password: API_USER_PASSWORD };
    const userIdpSel1 = { username: 'pwlf_sel1', password: API_USER_PASSWORD };
    const userIdpSel2 = { username: 'pwlf_sel2', password: API_USER_PASSWORD };

    await quietly(() => deleteIdp(domain.id, adminToken, `default-idp-${domain.id}`));

    const idpIf = await quietly(() =>
      createIdp(domain.id, adminToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        name: uniqueTestName('pwlf-idp-if'),
        configuration: JSON.stringify({
          users: [
            {
              firstname: 'If',
              lastname: 'User',
              username: userIdentifierFirst.username,
              password: userIdentifierFirst.password,
            },
          ],
        }),
      }),
    );

    const idpA = await quietly(() =>
      createIdp(domain.id, adminToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        name: uniqueTestName('pwlf-idp-a'),
        configuration: JSON.stringify({
          users: [
            {
              firstname: 'Sel',
              lastname: 'One',
              username: userIdpSel1.username,
              password: userIdpSel1.password,
            },
          ],
        }),
      }),
    );

    const idpB = await quietly(() =>
      createIdp(domain.id, adminToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        name: uniqueTestName('pwlf-idp-b'),
        configuration: JSON.stringify({
          users: [
            {
              firstname: 'Sel',
              lastname: 'Two',
              username: userIdpSel2.username,
              password: userIdpSel2.password,
            },
          ],
        }),
      }),
    );

    const appIdentifierFirst = await quietly(() =>
      createTestApp(uniqueTestName('pwlf-app-if'), domain, adminToken, 'WEB', {
        identityProviders: new Set([{ identity: idpIf.id, priority: 0 }]),
        settings: {
          oauth: OAUTH_WEB,
          login: { identifierFirstEnabled: true, inherited: false },
          advanced: { skipConsent: true },
        },
      }),
    );

    const appIdpDual = await quietly(() =>
      createTestApp(uniqueTestName('pwlf-app-idp-sel'), domain, adminToken, 'WEB', {
        identityProviders: new Set([
          {
            identity: idpA.id,
            selectionRule: "{#request.params['username'] matches 'pwlf_sel1' }",
            priority: -1,
          },
          {
            identity: idpB.id,
            selectionRule: "{#request.params['username'] matches 'pwlf_sel2' }",
            priority: -1,
          },
        ]),
        settings: {
          oauth: OAUTH_WEB,
          advanced: { skipConsent: true },
        },
      }),
    );

    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));
    await waitForOidcReady(domain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    const baseUrl = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
    const gatewayUrl = `${baseUrl}/${domain.hrid}`;

    const bundle: LoginFlowsGatewayBundle = {
      domain,
      adminToken,
      gatewayUrl,
      appIdentifierFirst,
      appIdpDual,
      userIdentifierFirst,
      userIdpSel1,
      userIdpSel2,
    };

    await use(bundle);

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },
});

export { expect } from '@playwright/test';
