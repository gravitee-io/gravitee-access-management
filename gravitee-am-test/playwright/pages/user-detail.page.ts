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

/** Page object for the user detail/profile page. */
export class UserDetailPage extends BasePage {
  /** Navigate directly to a user's detail page. */
  async navigateTo(domainId: string, userId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/users/${userId}`);
  }

  get displayName(): Locator {
    return this.page.locator('[data-testid="userDisplayName"]').first();
  }

  get usernameDisplay(): Locator {
    return this.page.locator('[data-testid="userUsername"]').first();
  }

  get firstNameInput(): Locator {
    return this.page.locator('input[name="firstName"]');
  }

  get lastNameInput(): Locator {
    return this.page.locator('input[name="lastName"]');
  }

  get usernameInput(): Locator {
    return this.page.locator('input[name="username"]');
  }

  /** Assert the displayed username matches. */
  async expectUsername(username: string): Promise<void> {
    await expect(this.usernameDisplay).toContainText(username);
  }

  /** Fill the first name field. */
  async fillFirstName(name: string): Promise<void> {
    await this.firstNameInput.fill(name);
  }

  /**
   * Fill the last name field.
   * Conditionally rendered via *ngIf="user.lastName" with two-way ngModel binding.
   * Using fill() or clear() sets the model to "" (falsy), removing the field from the DOM.
   * Triple-click selects all text, then pressSequentially() replaces without emptying.
   */
  async fillLastName(name: string): Promise<void> {
    await this.lastNameInput.waitFor({ state: 'visible' });
    await this.lastNameInput.click({ clickCount: 3 });
    await this.lastNameInput.pressSequentially(name);
  }

  /** Click the DELETE button in the danger zone. */
  async clickDelete(): Promise<void> {
    await this.page.locator('.gv-page-delete-zone button[color="warn"]').click();
  }

  get updateUsernameButton(): Locator {
    return this.page.locator('button[type="submit"]').filter({ hasText: /update username/i });
  }

  /**
   * Change the username via the separate username form.
   * Uses triple-click + pressSequentially to be safe if the input is *ngIf-bound.
   */
  async changeUsername(newUsername: string): Promise<void> {
    await this.usernameInput.click({ clickCount: 3 });
    await this.usernameInput.pressSequentially(newUsername);
    await this.updateUsernameButton.click();
    await this.confirmDialog();
  }
}
