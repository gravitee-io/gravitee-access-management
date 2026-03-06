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
import { Locator, expect } from '@playwright/test';
import { BasePage } from './base.page';

/** Page object for Domain > Settings > Users list page. */
export class DomainUsersPage extends BasePage {
  /** Navigate directly to the users list page for a domain. */
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/users`);
  }

  get searchInput(): Locator {
    return this.page.locator('#users-search-input input[matInput], #users-search-input input');
  }

  get datatable(): Locator {
    return this.page.locator('ngx-datatable');
  }

  get addUserButton(): Locator {
    return this.page.locator('a[mat-fab], button[mat-fab]').first();
  }

  /** Search for users by typing a query and pressing Enter. */
  async searchUsers(query: string): Promise<void> {
    await this.searchInput.clear();
    await this.searchInput.fill(query);
    await this.searchInput.press('Enter');
    await this.waitForReady();
  }

  /** Assert a user with the given username appears in the datatable. */
  async expectUserInTable(username: string): Promise<void> {
    const row = this.datatable.locator('.datatable-body-row').filter({ hasText: username });
    await expect(row.first()).toBeVisible();
  }

  /** Assert a user with the given username does NOT appear in the datatable. */
  async expectUserNotInTable(username: string): Promise<void> {
    // Positive anchor: prove the datatable rendered before asserting absence
    await expect(this.datatable).toBeVisible();
    const row = this.datatable.locator('.datatable-body-row').filter({ hasText: username });
    await expect(row).toHaveCount(0);
  }

  /** Click on a user in the datatable by username. */
  async clickUser(username: string): Promise<void> {
    const userLink = this.datatable.locator('a').filter({ hasText: username }).first();
    await userLink.click();
    await this.waitForReady();
  }

  /** Click the add user FAB button. */
  async clickAddUser(): Promise<void> {
    await this.addUserButton.click();
    await this.waitForReady();
  }
}
