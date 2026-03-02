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
import { Page, expect } from '@playwright/test';

/**
 * Page object for the gateway-rendered login form.
 * Not an Angular SPA page — served by /auth/authorize.
 */
export class LoginPage {
  constructor(private readonly page: Page) {}

  get usernameInput() {
    return this.page.locator('#username');
  }

  get passwordInput() {
    return this.page.locator('#password');
  }

  get signInButton() {
    return this.page.locator('#submitBtn');
  }

  get formTitle() {
    return this.page.locator('.title span');
  }

  get errorMessage() {
    return this.page.locator('.error-text');
  }

  /** Navigate to console (triggers OAuth redirect to login). */
  async goto(): Promise<void> {
    await this.page.goto('/');
    await this.page.waitForURL(/.*login.*|.*auth\/authorize.*/i, { timeout: 30_000 });
  }

  /** Log in and wait for redirect back to the Angular app. */
  async login(username: string, password: string): Promise<void> {
    await this.usernameInput.waitFor({ state: 'visible', timeout: 15_000 });
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.signInButton.click();

    await this.page.waitForURL(/.*(?:environments|dashboard|domains).*/i, { timeout: 30_000 });
  }

  async expectLoginFormVisible(): Promise<void> {
    await expect(this.usernameInput).toBeVisible();
    await expect(this.passwordInput).toBeVisible();
    await expect(this.signInButton).toBeVisible();
  }

  async expectError(text?: string): Promise<void> {
    await expect(this.errorMessage).toBeVisible();
    if (text) {
      await expect(this.errorMessage).toContainText(text);
    }
  }
}
