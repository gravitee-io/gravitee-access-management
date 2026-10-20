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
import { test as setup } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { ADMIN_PASSWORD } from '../utils/test-constants';

const ADMIN_NEXT_STORAGE_STATE = 'playwright/fixtures/.auth/admin-next.json';

/**
 * Authenticate once against the React console and save the browser state for the console-next project. The React
 * console sends the browser to the same Management API login form as the Angular console.
 */
// eslint-disable-next-line playwright/expect-expect -- setup fixture, no assertions needed
setup('authenticate as admin on the React console', async ({ page }) => {
  const loginPage = new LoginPage(page);

  await loginPage.goto();
  await loginPage.login(process.env.AM_ADMIN_USERNAME || 'admin', process.env.AM_ADMIN_PASSWORD || ADMIN_PASSWORD);

  await page.context().storageState({ path: ADMIN_NEXT_STORAGE_STATE });
});
