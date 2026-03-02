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

// Polyfill fetch for Node < 18 (AM SDK expects global fetch).
if (typeof globalThis.fetch === 'undefined') {
  globalThis.fetch = require('cross-fetch');
}

// Reuse the existing AM test API layer.
import { requestAdminAccessToken } from '../../api/commands/management/token-management-commands';
import { createDomain, deleteDomain, startDomain, waitForDomainSync } from '../../api/commands/management/domain-management-commands';
import { createApplication, deleteApplication } from '../../api/commands/management/application-management-commands';
import { Domain, Application } from '../../api/management/models';

import { HomePage } from '../pages/home.page';
import { LoginPage } from '../pages/login.page';

import faker from 'faker';

/** Playwright fixtures for AM UI tests. API-first setup, UI-first assertions. */

const uniqueName = (prefix: string) =>
  `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;

export type AmFixtures = {
  adminToken: string;
  homePage: HomePage;
  loginPage: LoginPage;
  /** Fresh domain, cleaned up after test. */
  testDomain: Domain;
  /** Fresh application in testDomain, cleaned up after test. */
  testApplication: Application;
};

export const test = base.extend<AmFixtures>({
  adminToken: async ({}, use) => {
    const token = await requestAdminAccessToken();
    await use(token);
  },

  homePage: async ({ page }, use) => {
    await use(new HomePage(page));
  },

  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },

  testDomain: async ({ adminToken }, use) => {
    const name = uniqueName('pw-domain');
    const domain = await createDomain(adminToken, name, faker.company.catchPhraseDescriptor());
    await startDomain(domain.id, adminToken);

    await waitForDomainSync(domain.id);

    await use(domain);

    try { await deleteDomain(domain.id, adminToken); } catch { /* already deleted */ }
  },

  testApplication: async ({ adminToken, testDomain }, use) => {
    const app = await createApplication(testDomain.id, adminToken, {
      name: uniqueName('pw-app'),
      type: 'SERVICE',
    });

    await use(app);

    try { await deleteApplication(testDomain.id, adminToken, app.id); } catch { /* already deleted */ }
  },
});

export { expect } from '@playwright/test';
