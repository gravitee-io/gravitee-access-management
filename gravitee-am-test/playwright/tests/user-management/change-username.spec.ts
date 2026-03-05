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
import { linkJira } from '../../utils/jira';
import { UserDetailPage } from '../../pages/user-detail.page';
import { createUser, deleteUser } from '../../../api/commands/management/user-management-commands';
import { UI_USER_PASSWORD } from '../../utils/test-constants';

test.describe('User Management — Change Username', () => {
  test('AM-2241: change username successfully', async ({ page, testDomain, testUser }, testInfo) => {
    linkJira(testInfo, 'AM-2241');

    const detailPage = new UserDetailPage(page);
    await detailPage.navigateTo(testDomain.id, testUser.id);

    // AM-2241 specifically tests usernames "including a space"
    const newUsername = `${testUser.username} renamed`;
    await detailPage.changeUsername(newUsername);
    await detailPage.expectSnackbar('updated');
  });

  test('duplicate username rejected', async ({ page, adminToken, testDomain, testUser }) => {
    // Create a second user to test duplicate username rejection
    const secondUser = await createUser(testDomain.id, adminToken, {
      firstName: 'Second',
      lastName: 'User',
      email: `pw-second-${Date.now()}@example.com`,
      username: `pw-second-${Date.now().toString(36)}`,
      password: UI_USER_PASSWORD,
      preRegistration: false,
    });

    try {
      const detailPage = new UserDetailPage(page);
      await detailPage.navigateTo(testDomain.id, secondUser.id);

      // Try to rename to the existing testUser's username
      await detailPage.changeUsername(testUser.username);

      // Should show error snackbar
      await detailPage.expectSnackbar(/already exists|error|invalid/i);
    } finally {
      try { await deleteUser(testDomain.id, adminToken, secondUser.id); } catch { /* best effort */ }
    }
  });

  test('AM-2250: changed username visible in user detail', async ({ page, testDomain, testUser }, testInfo) => {
    linkJira(testInfo, 'AM-2250');

    const detailPage = new UserDetailPage(page);
    await detailPage.navigateTo(testDomain.id, testUser.id);

    // Verify the username component is visible (AM-2250: "Check Update username component added")
    await expect(detailPage.usernameInput).toBeVisible();
    await expect(detailPage.updateUsernameButton).toBeVisible();

    const newUsername = `${testUser.username}-visible`;
    await detailPage.changeUsername(newUsername);
    await detailPage.expectSnackbar('updated');

    // Reload and verify the new username is displayed
    await page.reload();
    await detailPage.waitForReady();
    await detailPage.expectUsername(newUsername);
  });
});
