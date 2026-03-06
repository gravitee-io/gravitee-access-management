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
import { DomainUsersPage } from '../../pages/domain-users.page';
import { UserCreationPage } from '../../pages/user-creation.page';
import { UserDetailPage } from '../../pages/user-detail.page';
import { deleteUser } from '../../../api/commands/management/user-management-commands';
import { uniqueTestName } from '../../utils/fixture-helpers';
import { UI_USER_PASSWORD } from '../../utils/test-constants';

test.describe('User Management — CRUD', () => {
  test('view users list', async ({ page, testDomain }) => {
    const usersPage = new DomainUsersPage(page);
    await usersPage.navigateTo(testDomain.id);

    await expect(usersPage.datatable).toBeVisible();
  });

  test('search users by username', async ({ page, testDomain, testUser }) => {
    const usersPage = new DomainUsersPage(page);
    await usersPage.navigateTo(testDomain.id);

    await usersPage.searchUsers(testUser.username);
    await usersPage.expectUserInTable(testUser.username);
  });

  test('search users by email', async ({ page, testDomain, testUser }) => {
    const usersPage = new DomainUsersPage(page);
    await usersPage.navigateTo(testDomain.id);

    await usersPage.searchUsers(testUser.email);
    await usersPage.expectUserInTable(testUser.username);
  });

  test('AM-2201: create user via UI wizard', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-2201');
    const usersPage = new DomainUsersPage(page);
    await usersPage.navigateTo(testDomain.id);
    await usersPage.clickAddUser();

    const creationPage = new UserCreationPage(page);
    const username = uniqueTestName('pw-ui-user');
    await creationPage.fillFirstName('UITest');
    await creationPage.fillLastName('User');
    await creationPage.fillEmail(`${username}@example.com`);
    await creationPage.fillUsername(username);
    await creationPage.fillPassword(UI_USER_PASSWORD);
    await creationPage.clickCreate();

    await creationPage.waitForReady();
    // Should redirect to user detail or users list
    await expect(page).toHaveURL(/.*users.*/);

    // Clean up: find user ID from URL or navigate back and verify
    const url = page.url();
    const match = url.match(/users\/([^/]+)/);
    // eslint-disable-next-line playwright/no-conditional-in-test -- cleanup: delete only if created
    if (match && match[1] !== 'new') {
      try { await deleteUser(testDomain.id, adminToken, match[1]); } catch { /* best effort */ }
    }
  });

  test('view user detail page', async ({ page, testDomain, testUser }) => {
    const detailPage = new UserDetailPage(page);
    await detailPage.navigateTo(testDomain.id, testUser.id);

    await expect(detailPage.displayName).toBeVisible();
    await detailPage.expectUsername(testUser.username);
  });

  test('AM-2206: update user profile and change username', async ({ page, testDomain, testUser }, testInfo) => {
    linkJira(testInfo, 'AM-2206');
    const detailPage = new UserDetailPage(page);
    await detailPage.navigateTo(testDomain.id, testUser.id);

    await detailPage.fillFirstName('UpdatedFirst');
    await detailPage.fillLastName('UpdatedLast');
    await detailPage.clickSave();
    await detailPage.expectSnackbar('updated');

    // Change username (part of AM-2206: "Update an end-user including change username")
    const newUsername = `${testUser.username}-updated`;
    await detailPage.changeUsername(newUsername);
    await detailPage.expectSnackbar('updated');

    // Verify all changes persist
    await page.reload();
    await detailPage.waitForReady();
    await expect(detailPage.firstNameInput).toHaveValue('UpdatedFirst');
    await expect(detailPage.lastNameInput).toHaveValue('UpdatedLast');
    await detailPage.expectUsername(newUsername);
  });

  test('AM-2227: delete user from detail page', async ({ page, testDomain, testUser }, testInfo) => {
    linkJira(testInfo, 'AM-2227');
    const detailPage = new UserDetailPage(page);
    await detailPage.navigateTo(testDomain.id, testUser.id);

    await detailPage.clickDelete();
    await detailPage.confirmDialog();

    // Should redirect back to users list
    await detailPage.waitForReady();
    await expect(page).toHaveURL(/.*users.*/);

    // Verify user is no longer in the list
    const usersPage = new DomainUsersPage(page);
    await usersPage.expectUserNotInTable(testUser.username);
  });
});
