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
import { Locator } from '@playwright/test';
import { BasePage } from './base.page';

/** Page object for Domain > Settings > General. */
export class DomainGeneralPage extends BasePage {
  /** Navigate directly to a domain's general settings page. */
  async navigateTo(domainId: string): Promise<void> {
    await this.navigate(`/environments/${this.envHrid}/domains/${domainId}/settings/general`);
  }

  get nameInput(): Locator {
    return this.page.locator('input[name="name"]');
  }

  get descriptionInput(): Locator {
    return this.page.locator('textarea[name="description"]');
  }

  get enableToggle(): Locator {
    return this.page.locator('mat-slide-toggle').filter({ hasText: /enable domain/i });
  }

  /** Update the domain name. */
  async fillName(name: string): Promise<void> {
    await this.nameInput.clear();
    await this.nameInput.fill(name);
  }

  /** Update the domain description. */
  async fillDescription(desc: string): Promise<void> {
    await this.descriptionInput.clear();
    await this.descriptionInput.fill(desc);
  }

  /** Toggle the domain enable/disable switch. */
  async toggleEnabled(): Promise<void> {
    await this.enableToggle.click();
  }

  /** Click the DELETE button in the danger zone. */
  async clickDelete(): Promise<void> {
    await this.page.locator('gv-button[danger], button[color="warn"]').filter({ hasText: /delete/i }).click();
  }
}
