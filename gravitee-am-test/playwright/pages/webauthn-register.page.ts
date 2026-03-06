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
 * Page object for the gateway WebAuthn registration page (webauthn_register.html).
 *
 * The registration flow:
 * 1. User lands on this page after standard login
 * 2. Clicks the register button -> JS calls navigator.credentials.create()
 * 3. Virtual authenticator intercepts the call and creates a credential
 * 4. JS submits the form with the assertion
 * 5. On success, redirects to webauthn_register_success.html (device naming)
 */
export class WebAuthnRegisterPage {
  constructor(private readonly page: Page) {}

  get registerButton() {
    return this.page.locator('#register button.button.primary');
  }

  get skipLink() {
    return this.page.locator('a[href*="skipAction"], a:has(span.icons:text("arrow_forward"))');
  }

  get errorElement() {
    return this.page.locator('#webauthn-error');
  }

  get deviceNameInput() {
    return this.page.locator('#deviceName');
  }

  get deviceNameSubmitButton() {
    return this.page.locator('#form button.button.primary');
  }

  async expectRegistrationPage(): Promise<void> {
    await this.page.waitForURL(/.*\/webauthn\/register.*/);
    await expect(this.registerButton).toBeVisible();
  }

  /** Click the register button and wait for the credential creation flow to complete. */
  async clickRegister(): Promise<void> {
    await this.registerButton.click();
  }

  /** Wait for the registration success page (device naming form). */
  async waitForRegistrationSuccess(): Promise<void> {
    await this.page.waitForURL(/.*\/webauthn\/register\/success.*/, { timeout: 15000 });
    await expect(this.deviceNameInput).toBeVisible();
  }

  /** Submit the device name on the success page. */
  async submitDeviceName(name = 'Test Device'): Promise<void> {
    await this.deviceNameInput.clear();
    await this.deviceNameInput.fill(name);
    await this.deviceNameSubmitButton.click();
  }

  /** Full registration flow: click register -> wait for success -> name device. */
  async completeRegistration(deviceName = 'Test Device'): Promise<void> {
    await this.clickRegister();
    await this.waitForRegistrationSuccess();
    await this.submitDeviceName(deviceName);
  }

  async clickSkip(): Promise<void> {
    await this.skipLink.click();
  }
}
