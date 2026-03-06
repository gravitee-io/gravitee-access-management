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

// Must be first import — registers path aliases + @jest/globals shim.
import './playwright/utils/register-paths';

import { defineConfig, devices } from '@playwright/test';

// Env var defaults (mirrors api/config/dev.setup.js).
process.env.AM_UI_URL = process.env.AM_UI_URL || 'http://localhost:4200';
process.env.AM_MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL || 'http://localhost:8093';
process.env.AM_MANAGEMENT_ENDPOINT = process.env.AM_MANAGEMENT_ENDPOINT || process.env.AM_MANAGEMENT_URL + '/management';
process.env.AM_GATEWAY_URL = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
process.env.AM_GATEWAY_NODE_MONITORING_URL = process.env.AM_GATEWAY_NODE_MONITORING_URL || 'http://localhost:18092/_node';
process.env.AM_ADMIN_USERNAME = process.env.AM_ADMIN_USERNAME || 'admin';
process.env.AM_ADMIN_PASSWORD = process.env.AM_ADMIN_PASSWORD || 'adminadmin';
process.env.AM_DEF_ORG_ID = process.env.AM_DEF_ORG_ID || 'DEFAULT';
process.env.AM_DEF_ENV_ID = process.env.AM_DEF_ENV_ID || 'DEFAULT';
// The Angular router uses the environment hrid (lowercase), not the ID.
process.env.AM_DEF_ENV_HRID = process.env.AM_DEF_ENV_HRID || 'default';

export default defineConfig({
  globalTeardown: './playwright/fixtures/global.teardown.ts',
  testDir: './playwright/tests',
  outputDir: './playwright/test-results',

  timeout: 60_000,
  expect: { timeout: 15_000 },

  fullyParallel: true,
  workers: process.env.CI ? 3 : undefined,

  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,

  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright/playwright-report' }], ['junit', { outputFile: 'playwright/junit-results/junit.xml' }]]
    : [['html', { open: 'on-failure', outputFolder: 'playwright/playwright-report' }]],

  use: {
    baseURL: process.env.AM_UI_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  projects: [
    {
      name: 'setup',
      testMatch: /global\.setup\.ts/,
      testDir: './playwright/fixtures',
    },

    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'playwright/fixtures/.auth/admin.json',
      },
      dependencies: ['setup'],
    },

    // TODO: Enable Firefox cross-browser validation once Chromium suite is stable.
    // Requires `npx playwright install firefox` and CI pipeline update.
  ],
});
