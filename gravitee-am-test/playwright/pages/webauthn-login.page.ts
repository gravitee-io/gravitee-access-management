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
 * Page object for the gateway WebAuthn login page (webauthn_login.html).
 *
 * The passwordless login flow:
 * 1. User navigates to this page (via passwordless link on login page)
 * 2. Enters username and clicks the login button
 * 3. JS calls navigator.credentials.get() with the server-provided challenge
 * 4. Virtual authenticator returns the matching credential
 * 5. JS submits the form with the assertion -> user is authenticated
 */
export class WebAuthnLoginPage {
  constructor(private readonly page: Page) {}

  get usernameInput() {
    return this.page.locator('#username');
  }

  get loginButton() {
    return this.page.locator('#login button.button.primary');
  }

  get backToSignInLink() {
    return this.page.locator('a:has(span.icons:text("arrow_back"))');
  }

  get errorElement() {
    return this.page.locator('#webauthn-error');
  }

  get serverError() {
    return this.page.locator('.item.error-text:not(#webauthn-error)');
  }

  get enforcePasswordMessage() {
    return this.page.locator('.item.error-text:has(.error)');
  }

  async expectPasswordlessLoginPage(): Promise<void> {
    await this.page.waitForURL(/.*\/webauthn\/login.*/);
    await expect(this.loginButton).toBeVisible();
  }

  async enterUsername(username: string): Promise<void> {
    await this.usernameInput.waitFor({ state: 'visible' });
    await this.usernameInput.fill(username);
  }

  /** Click the login button to trigger navigator.credentials.get(). */
  async submitPasswordless(): Promise<void> {
    await this.loginButton.click();
  }

  /** Full passwordless login: enter username -> click login -> wait for redirect. */
  async loginPasswordless(username: string, redirectPattern?: RegExp): Promise<void> {
    await this.enterUsername(username);
    await this.submitPasswordless();
    if (redirectPattern) {
      await this.page.waitForURL(redirectPattern, { timeout: 15000 });
    }
  }

  async clickBackToSignIn(): Promise<void> {
    await this.backToSignInLink.click();
  }

  async expectError(): Promise<void> {
    await expect(this.errorElement).toBeVisible();
  }

  async expectEnforcePasswordMessage(): Promise<void> {
    await expect(this.enforcePasswordMessage).toBeVisible();
  }
}
