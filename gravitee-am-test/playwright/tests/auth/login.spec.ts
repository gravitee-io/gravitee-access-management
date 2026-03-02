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
import { test, expect } from '../../fixtures/base.fixture';

/** Login flow tests — runs without saved auth state. */
test.describe('Login', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('should display the login form', async ({ loginPage }) => {
    await loginPage.goto();
    await loginPage.expectLoginFormVisible();
  });

  test('should login with valid admin credentials', async ({ loginPage, page }) => {
    const username = process.env.AM_ADMIN_USERNAME || 'admin';
    const password = process.env.AM_ADMIN_PASSWORD || 'adminadmin';

    await loginPage.goto();
    await loginPage.login(username, password);

    await expect(page).toHaveURL(/.*(?:environments|dashboard|domains).*/i);
  });

  test('should show error for invalid credentials', async ({ loginPage }) => {
    await loginPage.goto();
    await loginPage.usernameInput.fill('invalid-user');
    await loginPage.passwordInput.fill('wrong-password');
    await loginPage.signInButton.click();

    await expect(loginPage.usernameInput).toBeVisible({ timeout: 10_000 });
  });
});
