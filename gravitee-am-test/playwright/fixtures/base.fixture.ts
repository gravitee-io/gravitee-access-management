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

// Always use cross-fetch — matches E2E test-fixture pattern.
// Native Node 18+ fetch can behave differently with the generated SDK.
import crossFetch from 'cross-fetch';
globalThis.fetch = crossFetch;

// Reuse the existing AM test API layer.
import { requestAdminAccessToken } from '../../api/commands/management/token-management-commands';
import { createDomain, startDomain, waitForDomainSync, safeDeleteDomain } from '../../api/commands/management/domain-management-commands';
import { createApplication, deleteApplication } from '../../api/commands/management/application-management-commands';
import { createUser, deleteUser } from '../../api/commands/management/user-management-commands';
import { Domain, Application, User } from '../../api/management/models';

import { HomePage } from '../pages/home.page';
import { LoginPage } from '../pages/login.page';
import { quietly, uniqueTestName as uniqueName } from '../utils/fixture-helpers';
import { UI_USER_PASSWORD } from '../utils/test-constants';

export type AmFixtures = {
  adminToken: string;
  homePage: HomePage;
  loginPage: LoginPage;
  /** Fresh domain, cleaned up after test. */
  testDomain: Domain;
  /** Fresh application in testDomain, cleaned up after test. */
  testApplication: Application;
  /** Fresh AGENT-type application in testDomain, cleaned up after test. */
  testAgenticApp: Application;
  /** Fresh user in testDomain, cleaned up after test. */
  testUser: User;
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
    const domain = await quietly(() => createDomain(adminToken, name, 'Playwright test domain'));
    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));

    await use(domain);

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },

  testApplication: async ({ adminToken, testDomain }, use) => {
    const app = await createApplication(testDomain.id, adminToken, {
      name: uniqueName('pw-app'),
      type: 'SERVICE',
    });

    await use(app);

    await quietly(async () => {
      try {
        await deleteApplication(testDomain.id, adminToken, app.id);
      } catch (e: unknown) {
        // 404 expected if domain teardown already cascaded; log anything else
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- error shape is unknown SDK type
        if (e && typeof e === 'object' && 'response' in e && (e as any).response?.status !== 404) {
          console.warn(`testApplication teardown: ${e}`);
        }
      }
    });
  },

  testAgenticApp: async ({ adminToken, testDomain }, use) => {
    const app = await createApplication(testDomain.id, adminToken, {
      name: uniqueName('pw-agent'),
      type: 'AGENT',
      redirectUris: ['https://gravitee.io/callback'],
    });

    await use(app);

    await quietly(async () => {
      try {
        await deleteApplication(testDomain.id, adminToken, app.id);
      } catch (e: unknown) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- error shape is unknown SDK type
        if (e && typeof e === 'object' && 'response' in e && (e as any).response?.status !== 404) {
          console.warn(`testAgenticApp teardown: ${e}`);
        }
      }
    });
  },

  testUser: async ({ adminToken, testDomain }, use) => {
    const user = await createUser(testDomain.id, adminToken, {
      firstName: 'PW',
      lastName: 'Test',
      email: `${uniqueName('pw-test')}@example.com`,
      username: uniqueName('pw-user'),
      password: UI_USER_PASSWORD,
      preRegistration: false,
    });

    await use(user);

    await quietly(async () => {
      try {
        await deleteUser(testDomain.id, adminToken, user.id);
      } catch (e: unknown) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any -- error shape is unknown SDK type
        if (e && typeof e === 'object' && 'response' in e && (e as any).response?.status !== 404) {
          console.warn(`testUser teardown: ${e}`);
        }
      }
    });
  },
});

export { expect } from '@playwright/test';
