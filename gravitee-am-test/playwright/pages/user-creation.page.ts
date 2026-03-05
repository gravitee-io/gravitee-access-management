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
import { BasePage } from './base.page';

/** Page object for the user creation form. */
export class UserCreationPage extends BasePage {
  async fillFirstName(name: string): Promise<void> {
    await this.page.locator('input[name="firstName"]').fill(name);
  }

  async fillLastName(name: string): Promise<void> {
    await this.page.locator('input[name="lastName"]').fill(name);
  }

  async fillEmail(email: string): Promise<void> {
    await this.page.locator('input[name="email"]').fill(email);
  }

  async fillUsername(username: string): Promise<void> {
    await this.page.locator('input[name="username"]').fill(username);
  }

  async fillPassword(password: string): Promise<void> {
    await this.page.locator('input[name="password"]').fill(password);
  }

  /** Submit the creation form. */
  async clickCreate(): Promise<void> {
    await this.page.locator('button[type="submit"]').filter({ hasText: /create/i }).click();
  }
}
