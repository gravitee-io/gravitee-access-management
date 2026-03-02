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

const ADMIN_STORAGE_STATE = 'playwright/fixtures/.auth/admin.json';

/** Authenticate once and save browser state for all tests. */
setup('authenticate as admin', async ({ page }) => {
  const loginPage = new LoginPage(page);
  const username = process.env.AM_ADMIN_USERNAME || 'admin';
  const password = process.env.AM_ADMIN_PASSWORD || 'adminadmin';

  await loginPage.goto();
  await loginPage.login(username, password);

  await page.context().storageState({ path: ADMIN_STORAGE_STATE });
});
